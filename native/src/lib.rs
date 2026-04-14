use std::collections::HashSet;
use std::sync::Mutex;

use webrtc_audio_processing::Processor;
use webrtc_audio_processing_config::*;

static HANDLES: Mutex<Option<HashSet<i64>>> = Mutex::new(None);

struct Instance {
    processor: Processor,
    config: Config,
}

fn init_handles() {
    let mut guard = HANDLES.lock().unwrap();
    if guard.is_none() {
        *guard = Some(HashSet::new());
    }
}

fn register_handle(ptr: i64) {
    init_handles();
    HANDLES.lock().unwrap().as_mut().unwrap().insert(ptr);
}

fn unregister_handle(ptr: i64) -> bool {
    init_handles();
    HANDLES.lock().unwrap().as_mut().unwrap().remove(&ptr)
}

fn with_instance<F, R>(handle: i64, f: F) -> R
where
    F: FnOnce(&mut Instance) -> R,
    R: Default,
{
    init_handles();
    {
        let guard = HANDLES.lock().unwrap();
        if !guard.as_ref().unwrap().contains(&handle) {
            return R::default();
        }
    }
    let instance = unsafe { &mut *(handle as *mut Instance) };
    f(instance)
}

/// Thread-safe read-only access to Instance.
/// Used by processing calls (process_capture, analyze_render, process_render)
/// which only need &self on the underlying Processor.
fn with_instance_ref<F, R>(handle: i64, f: F) -> R
where
    F: FnOnce(&Instance) -> R,
    R: Default,
{
    init_handles();
    {
        let guard = HANDLES.lock().unwrap();
        if !guard.as_ref().unwrap().contains(&handle) {
            return R::default();
        }
    }
    let instance = unsafe { &*(handle as *const Instance) };
    f(instance)
}

fn error_to_code(e: &webrtc_audio_processing::Error) -> i32 {
    use webrtc_audio_processing::Error::*;
    match e {
        Unspecified => -1,
        InitializationFailed => -2,
        UnsupportedComponent => -3,
        UnsupportedFunction => -4,
        NullPointer => -5,
        BadParameter => -6,
        BadSampleRate => -7,
        BadDataLength => -8,
        BadNumberChannels => -9,
        File => -10,
        StreamParameterNotSet => -11,
        NotEnabled => -12,
    }
}

fn i2b(v: i32) -> bool {
    v != 0
}

// --- Lifecycle ---

#[no_mangle]
pub extern "C" fn apm_processor_new(sample_rate_hz: u32) -> i64 {
    match Processor::new(sample_rate_hz) {
        Ok(processor) => {
            let instance = Box::new(Instance {
                processor,
                config: Config::default(),
            });
            let ptr = Box::into_raw(instance) as i64;
            register_handle(ptr);
            ptr
        }
        Err(e) => error_to_code(&e) as i64,
    }
}

#[no_mangle]
pub extern "C" fn apm_processor_destroy(handle: i64) {
    if unregister_handle(handle) {
        unsafe {
            drop(Box::from_raw(handle as *mut Instance));
        }
    }
}

#[no_mangle]
pub extern "C" fn apm_processor_reinitialize(handle: i64) {
    with_instance(handle, |inst| {
        inst.processor.reinitialize();
    });
}

#[no_mangle]
pub extern "C" fn apm_processor_num_samples_per_frame(handle: i64) -> u32 {
    with_instance(handle, |inst| inst.processor.num_samples_per_frame() as u32)
}

// --- Processing ---

#[no_mangle]
pub extern "C" fn apm_processor_process_capture(
    handle: i64,
    data: *mut f32,
    num_channels: u32,
    samples_per_channel: u32,
) -> i32 {
    if data.is_null() {
        return -5;
    }
    with_instance_ref(handle, |inst| {
        let total = (num_channels * samples_per_channel) as usize;
        let slice = unsafe { std::slice::from_raw_parts_mut(data, total) };
        let mut channels: Vec<Vec<f32>> = slice
            .chunks(samples_per_channel as usize)
            .map(|c| c.to_vec())
            .collect();
        match inst
            .processor
            .process_capture_frame(channels.iter_mut().map(|c| c.as_mut_slice()))
        {
            Ok(()) => {
                for (i, ch) in channels.iter().enumerate() {
                    let offset = i * samples_per_channel as usize;
                    slice[offset..offset + ch.len()].copy_from_slice(ch);
                }
                0
            }
            Err(e) => error_to_code(&e),
        }
    })
}

#[no_mangle]
pub extern "C" fn apm_processor_process_render(
    handle: i64,
    data: *mut f32,
    num_channels: u32,
    samples_per_channel: u32,
) -> i32 {
    if data.is_null() {
        return -5;
    }
    with_instance_ref(handle, |inst| {
        let total = (num_channels * samples_per_channel) as usize;
        let slice = unsafe { std::slice::from_raw_parts_mut(data, total) };
        let mut channels: Vec<Vec<f32>> = slice
            .chunks(samples_per_channel as usize)
            .map(|c| c.to_vec())
            .collect();
        match inst
            .processor
            .process_render_frame(channels.iter_mut().map(|c| c.as_mut_slice()))
        {
            Ok(()) => {
                for (i, ch) in channels.iter().enumerate() {
                    let offset = i * samples_per_channel as usize;
                    slice[offset..offset + ch.len()].copy_from_slice(ch);
                }
                0
            }
            Err(e) => error_to_code(&e),
        }
    })
}

#[no_mangle]
pub extern "C" fn apm_processor_analyze_render(
    handle: i64,
    data: *const f32,
    num_channels: u32,
    samples_per_channel: u32,
) -> i32 {
    if data.is_null() {
        return -5;
    }
    with_instance_ref(handle, |inst| {
        let total = (num_channels * samples_per_channel) as usize;
        let slice = unsafe { std::slice::from_raw_parts(data, total) };
        let channels: Vec<&[f32]> = slice.chunks(samples_per_channel as usize).collect();
        match inst.processor.analyze_render_frame(channels.into_iter()) {
            Ok(()) => 0,
            Err(e) => error_to_code(&e),
        }
    })
}

// --- Config setters ---
// All setters update inst.config only. Call apm_apply_config() to apply.
// Boolean parameters use i32 (0=false, nonzero=true) for JNA compatibility.

#[no_mangle]
pub extern "C" fn apm_set_pipeline(
    handle: i64,
    max_processing_rate: i32,
    multi_ch_render: i32,
    multi_ch_capture: i32,
    downmix_method: i32,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.pipeline = Pipeline {
            maximum_internal_processing_rate: match max_processing_rate {
                32000 => PipelineProcessingRate::Max32000Hz,
                _ => PipelineProcessingRate::Max48000Hz,
            },
            multi_channel_render: i2b(multi_ch_render),
            multi_channel_capture: i2b(multi_ch_capture),
            capture_downmix_method: match downmix_method {
                1 => DownmixMethod::UseFirstChannel,
                _ => DownmixMethod::Average,
            },
        };
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_apply_config(handle: i64) -> i32 {
    with_instance(handle, |inst| {
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_high_pass_filter(
    handle: i64,
    enabled: i32,
    apply_in_full_band: i32,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.high_pass_filter = if i2b(enabled) {
            Some(HighPassFilter {
                apply_in_full_band: i2b(apply_in_full_band),
            })
        } else {
            None
        };
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_echo_canceller(handle: i64, mode: i32, stream_delay_ms: i32) -> i32 {
    with_instance(handle, |inst| {
        inst.config.echo_canceller = match mode {
            1 => Some(EchoCanceller::Mobile {
                stream_delay_ms: stream_delay_ms.max(0) as u16,
            }),
            2 => Some(EchoCanceller::Full {
                stream_delay_ms: if stream_delay_ms < 0 {
                    None
                } else {
                    Some(stream_delay_ms as u16)
                },
            }),
            _ => None,
        };
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_noise_suppression(
    handle: i64,
    enabled: i32,
    level: i32,
    analyze_linear_aec_output: i32,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.noise_suppression = if i2b(enabled) {
            Some(NoiseSuppression {
                level: match level {
                    0 => NoiseSuppressionLevel::Low,
                    2 => NoiseSuppressionLevel::High,
                    3 => NoiseSuppressionLevel::VeryHigh,
                    _ => NoiseSuppressionLevel::Moderate,
                },
                analyze_linear_aec_output: i2b(analyze_linear_aec_output),
            })
        } else {
            None
        };
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_pre_amplifier(
    handle: i64,
    enabled: i32,
    fixed_gain_factor: f32,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.capture_amplifier = if i2b(enabled) {
            Some(CaptureAmplifier::PreAmplifier(PreAmplifier {
                fixed_gain_factor,
            }))
        } else {
            None
        };
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_capture_level_adjustment(
    handle: i64,
    enabled: i32,
    pre_gain: f32,
    post_gain: f32,
    mic_emu_enabled: i32,
    mic_emu_initial: u8,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.capture_amplifier = if i2b(enabled) {
            Some(CaptureAmplifier::CaptureLevelAdjustment(
                CaptureLevelAdjustment {
                    pre_gain_factor: pre_gain,
                    post_gain_factor: post_gain,
                    analog_mic_gain_emulation: if i2b(mic_emu_enabled) {
                        Some(AnalogMicGainEmulation {
                            initial_level: mic_emu_initial,
                        })
                    } else {
                        None
                    },
                },
            ))
        } else {
            None
        };
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_gain_controller1(
    handle: i64,
    enabled: i32,
    mode: i32,
    target_level_dbfs: u8,
    compression_gain_db: u8,
    enable_limiter: i32,
) -> i32 {
    with_instance(handle, |inst| {
        if i2b(enabled) {
            let gc1 = GainController1 {
                mode: match mode {
                    0 => GainControllerMode::AdaptiveAnalog,
                    1 => GainControllerMode::AdaptiveDigital,
                    _ => GainControllerMode::FixedDigital,
                },
                target_level_dbfs,
                compression_gain_db,
                enable_limiter: i2b(enable_limiter),
                analog_gain_controller: None,
            };
            inst.config.gain_controller = Some(GainController::GainController1(gc1));
        } else {
            inst.config.gain_controller = None;
        }
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_analog_gain_controller(
    handle: i64,
    enabled: i32,
    startup_min_vol: i32,
    clipped_level_min: i32,
    enable_digital_adaptive: i32,
    clipped_level_step: i32,
    clipped_ratio_threshold: f32,
    clipped_wait_frames: i32,
) -> i32 {
    with_instance(handle, |inst| {
        if let Some(GainController::GainController1(ref mut gc1)) = inst.config.gain_controller {
            gc1.analog_gain_controller = if i2b(enabled) {
                Some(AnalogGainController {
                    startup_min_volume: startup_min_vol,
                    clipped_level_min,
                    enable_digital_adaptive: i2b(enable_digital_adaptive),
                    clipped_level_step,
                    clipped_ratio_threshold,
                    clipped_wait_frames,
                    clipping_predictor: None,
                })
            } else {
                None
            };
            0
        } else {
            -6
        }
    })
}

#[no_mangle]
pub extern "C" fn apm_set_clipping_predictor(
    handle: i64,
    enabled: i32,
    mode: i32,
    window_length: i32,
    ref_window_length: i32,
    ref_window_delay: i32,
    clipping_threshold: f32,
    crest_factor_margin: f32,
    use_predicted_step: i32,
) -> i32 {
    with_instance(handle, |inst| {
        if let Some(GainController::GainController1(ref mut gc1)) = inst.config.gain_controller {
            if let Some(ref mut agc) = gc1.analog_gain_controller {
                agc.clipping_predictor = if i2b(enabled) {
                    Some(ClippingPredictor {
                        mode: match mode {
                            1 => ClippingPredictorMode::AdaptiveStepClippingPeakPrediction,
                            2 => ClippingPredictorMode::FixedStepClippingPeakPrediction,
                            _ => ClippingPredictorMode::ClippingEventPrediction,
                        },
                        window_length,
                        reference_window_length: ref_window_length,
                        reference_window_delay: ref_window_delay,
                        clipping_threshold,
                        crest_factor_margin,
                        use_predicted_step: i2b(use_predicted_step),
                    })
                } else {
                    None
                };
                0
            } else {
                -6
            }
        } else {
            -6
        }
    })
}

#[no_mangle]
pub extern "C" fn apm_set_gain_controller2(
    handle: i64,
    enabled: i32,
    input_vol_controller: i32,
    fixed_digital_gain_db: f32,
) -> i32 {
    with_instance(handle, |inst| {
        if i2b(enabled) {
            let gc2 = GainController2 {
                input_volume_controller_enabled: i2b(input_vol_controller),
                adaptive_digital: None,
                fixed_digital: FixedDigital {
                    gain_db: fixed_digital_gain_db,
                },
            };
            inst.config.gain_controller = Some(GainController::GainController2(gc2));
        } else {
            inst.config.gain_controller = None;
        }
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_adaptive_digital(
    handle: i64,
    enabled: i32,
    headroom_db: f32,
    max_gain_db: f32,
    initial_gain_db: f32,
    max_gain_change_db_per_sec: f32,
    max_output_noise_level_dbfs: f32,
) -> i32 {
    with_instance(handle, |inst| {
        if let Some(GainController::GainController2(ref mut gc2)) = inst.config.gain_controller {
            gc2.adaptive_digital = if i2b(enabled) {
                Some(AdaptiveDigital {
                    headroom_db,
                    max_gain_db,
                    initial_gain_db,
                    max_gain_change_db_per_second: max_gain_change_db_per_sec,
                    max_output_noise_level_dbfs,
                })
            } else {
                None
            };
            0
        } else {
            -6
        }
    })
}

// --- Stats & hints ---

#[no_mangle]
pub extern "C" fn apm_processor_get_stats(
    handle: i64,
    out_voice_detected: *mut i8,
    out_erl: *mut f64,
    out_erle: *mut f64,
    out_divergent_filter_fraction: *mut f64,
    out_delay_median_ms: *mut i32,
    out_delay_std_ms: *mut i32,
    out_residual_echo_likelihood: *mut f64,
    out_residual_echo_likelihood_recent_max: *mut f64,
    out_delay_ms: *mut i32,
) -> i32 {
    with_instance_ref(handle, |inst| {
        let stats = inst.processor.get_stats();
        unsafe {
            if !out_voice_detected.is_null() {
                *out_voice_detected = match stats.voice_detected {
                    Some(true) => 1,
                    Some(false) => 0,
                    None => -1,
                };
            }
            if !out_erl.is_null() {
                *out_erl = stats.echo_return_loss.unwrap_or(f64::NAN);
            }
            if !out_erle.is_null() {
                *out_erle = stats.echo_return_loss_enhancement.unwrap_or(f64::NAN);
            }
            if !out_divergent_filter_fraction.is_null() {
                *out_divergent_filter_fraction =
                    stats.divergent_filter_fraction.unwrap_or(f64::NAN);
            }
            if !out_delay_median_ms.is_null() {
                *out_delay_median_ms = stats.delay_median_ms.map(|v| v as i32).unwrap_or(-1);
            }
            if !out_delay_std_ms.is_null() {
                *out_delay_std_ms =
                    stats.delay_standard_deviation_ms.map(|v| v as i32).unwrap_or(-1);
            }
            if !out_residual_echo_likelihood.is_null() {
                *out_residual_echo_likelihood =
                    stats.residual_echo_likelihood.unwrap_or(f64::NAN);
            }
            if !out_residual_echo_likelihood_recent_max.is_null() {
                *out_residual_echo_likelihood_recent_max =
                    stats.residual_echo_likelihood_recent_max.unwrap_or(f64::NAN);
            }
            if !out_delay_ms.is_null() {
                *out_delay_ms = stats.delay_ms.map(|v| v as i32).unwrap_or(-1);
            }
        }
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_processor_set_output_will_be_muted(handle: i64, muted: i32) {
    with_instance(handle, |inst| {
        inst.processor.set_output_will_be_muted(i2b(muted));
    });
}

#[no_mangle]
pub extern "C" fn apm_processor_set_stream_key_pressed(handle: i64, pressed: i32) {
    with_instance(handle, |inst| {
        inst.processor.set_stream_key_pressed(i2b(pressed));
    });
}
