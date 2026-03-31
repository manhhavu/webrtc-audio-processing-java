# WebRTC Audio Processing Java Wrapper — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a Java wrapper for the webrtc-audio-processing Rust crate, publishable as Maven/Gradle dependencies with bundled native libraries.

**Architecture:** Rust FFI bridge (`cdylib`) exposes `extern "C"` functions. Java side uses JNA to call them. Config passed via per-group FFI setters. Two Gradle modules: API JAR + platform-specific natives JARs.

**Tech Stack:** Java 21+, Rust (bundled WebRTC C++), JNA 5.14, Gradle Kotlin DSL, GitHub Actions CI

**Spec:** `.dev/docs/design/2026-03-31-java-wrapper-design.md`

---

## File Map

| File | Responsibility |
|---|---|
| `.mise.toml` | Java 21 + Rust toolchain |
| `build.gradle.kts` | Root Gradle build (plugins, group/version) |
| `settings.gradle.kts` | Multi-module project settings |
| `gradle/libs.versions.toml` | Version catalog (JNA, JUnit) |
| `native/Cargo.toml` | Rust FFI bridge crate config |
| `native/src/lib.rs` | All `extern "C"` FFI functions |
| `webrtc-audio-processing/build.gradle.kts` | Java API module build |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ErrorCode.java` | Error code enum |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ProcessorException.java` | Exception class |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Stats.java` | Stats record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/PipelineProcessingRate.java` | Pipeline rate enum |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/DownmixMethod.java` | Downmix method enum |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Pipeline.java` | Pipeline config record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NoiseSuppressionLevel.java` | NS level enum |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NoiseSuppression.java` | NS config record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/HighPassFilter.java` | HPF config record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/EchoCanceller.java` | Echo canceller sealed interface |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/AnalogMicGainEmulation.java` | Mic gain emulation record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/CaptureAmplifier.java` | Capture amplifier sealed interface |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/GainControllerMode.java` | GC1 mode enum |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ClippingPredictorMode.java` | Clipping predictor mode enum |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ClippingPredictor.java` | Clipping predictor record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/AnalogGainController.java` | Analog GC record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/AdaptiveDigital.java` | Adaptive digital record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/FixedDigital.java` | Fixed digital record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/GainController.java` | Gain controller sealed interface |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Config.java` | Top-level config record |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NativeLib.java` | JNA interface (package-private) |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NativeLibLoader.java` | Platform detection + extraction |
| `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Processor.java` | Main public API class |
| `webrtc-audio-processing/src/test/java/com/manhhavu/webrtc/audio/ProcessorTest.java` | Integration tests |
| `webrtc-audio-processing/src/test/java/com/manhhavu/webrtc/audio/ConfigTest.java` | Config variant tests |
| `webrtc-audio-processing-natives/build.gradle.kts` | Natives packaging module |
| `.github/workflows/build.yml` | CI workflow |

---

### Task 1: Project Scaffolding

**Files:**
- Create: `.mise.toml`
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `webrtc-audio-processing/build.gradle.kts`
- Create: `webrtc-audio-processing-natives/build.gradle.kts`
- Create: `.gitignore`

- [ ] **Step 1: Create `.mise.toml`**

```toml
[tools]
java = "temurin-21"
rust = "latest"
```

- [ ] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    java
}

allprojects {
    group = "com.manhhavu"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "webrtc-audio-processing-java"

include("webrtc-audio-processing")
include("webrtc-audio-processing-natives")
```

- [ ] **Step 4: Create `gradle/libs.versions.toml`**

```toml
[versions]
jna = "5.14.0"
junit = "5.11.4"

[libraries]
jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
junit-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit" }
junit-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit" }

[plugins]
```

- [ ] **Step 5: Create `webrtc-audio-processing/build.gradle.kts`**

```kotlin
plugins {
    `java-library`
}

dependencies {
    api(libs.jna)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
```

- [ ] **Step 6: Create `webrtc-audio-processing-natives/build.gradle.kts`**

This module packages the pre-compiled native libraries into classifier JARs.

```kotlin
plugins {
    `java-library`
}

// Native libs are placed here by the cargo build task or CI:
//   build/natives/macos-aarch64/libapm.dylib
//   build/natives/linux-x86_64/libapm.so

val nativesDir = layout.buildDirectory.dir("natives")

tasks.register("cargoReleaseBuild") {
    description = "Build the Rust native library for the current platform"
    doLast {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch")
        val platform = when {
            "mac" in os && arch == "aarch64" -> "macos-aarch64"
            "mac" in os -> "macos-x86_64"
            "linux" in os && (arch == "amd64" || arch == "x86_64") -> "linux-x86_64"
            "linux" in os && arch == "aarch64" -> "linux-aarch64"
            else -> error("Unsupported platform: $os $arch")
        }
        val libName = if ("mac" in os) "libapm.dylib" else "libapm.so"

        exec {
            workingDir = rootProject.file("native")
            commandLine("cargo", "build", "--release")
        }

        val src = rootProject.file("native/target/release/$libName")
        val dest = nativesDir.get().dir(platform).file(libName).asFile
        dest.parentFile.mkdirs()
        src.copyTo(dest, overwrite = true)
    }
}

// For each platform, create a JAR with classifier
val platforms = listOf("macos-aarch64", "linux-x86_64")
platforms.forEach { platform ->
    tasks.register<Jar>("${platform}Jar") {
        archiveClassifier.set(platform)
        from(nativesDir.map { it.dir(platform) }) {
            into("native/$platform")
        }
    }
}

tasks.named("assemble") {
    dependsOn(platforms.map { "${it}Jar" })
}
```

- [ ] **Step 7: Update `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/

# IDE
.idea/
*.iml
.vscode/

# Rust
native/target/

# OS
.DS_Store
```

- [ ] **Step 8: Initialize Gradle wrapper**

Run: `cd /Users/manhha/Developer/madeformed/webrtc-audio-processing-java && gradle wrapper --gradle-version 8.12`

Expected: `gradle/wrapper/` directory created with `gradle-wrapper.jar` and `gradle-wrapper.properties`.

- [ ] **Step 9: Verify Gradle builds**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL (no source files yet, but project structure is valid).

- [ ] **Step 10: Commit**

```bash
git add .mise.toml build.gradle.kts settings.gradle.kts gradle/ gradlew gradlew.bat \
  webrtc-audio-processing/build.gradle.kts webrtc-audio-processing-natives/build.gradle.kts .gitignore
git commit -m "feat: scaffold Gradle multi-module project with mise config"
```

---

### Task 2: Rust FFI Bridge

**Files:**
- Create: `native/Cargo.toml`
- Create: `native/src/lib.rs`

**Reference:** The existing bridge at `../dev-svi-nova/webrtc-apm-bridge/` uses `Box::into_raw()` as i64 handles. We expand this pattern to support the full config API.

- [ ] **Step 1: Create `native/Cargo.toml`**

```toml
[package]
name = "webrtc-apm-java"
version = "0.1.0"
edition = "2021"

[lib]
name = "apm"
crate-type = ["cdylib"]

[dependencies]
webrtc-audio-processing = { path = "../../webrtc-audio-processing", features = ["bundled"] }
webrtc-audio-processing-config = { path = "../../webrtc-audio-processing/webrtc-audio-processing-config" }
```

Note: `path = "../../webrtc-audio-processing"` references the sibling clone at `../webrtc-audio-processing`.

- [ ] **Step 2: Create `native/src/lib.rs` — handle management and lifecycle**

```rust
use std::collections::HashSet;
use std::sync::Mutex;

use webrtc_audio_processing::Processor;
use webrtc_audio_processing_config as config;

static HANDLES: Mutex<Option<HashSet<i64>>> = Mutex::new(None);

struct Instance {
    processor: Processor,
    config: config::Config,
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
    let guard = HANDLES.lock().unwrap();
    if !guard.as_ref().unwrap().contains(&handle) {
        return R::default();
    }
    drop(guard);
    let instance = unsafe { &mut *(handle as *mut Instance) };
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

#[no_mangle]
pub extern "C" fn apm_processor_new(sample_rate_hz: u32) -> i64 {
    match Processor::new(sample_rate_hz) {
        Ok(processor) => {
            let instance = Box::new(Instance {
                processor,
                config: config::Config::default(),
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
```

- [ ] **Step 3: Add processing functions to `native/src/lib.rs`**

Append to `lib.rs`:

```rust
#[no_mangle]
pub extern "C" fn apm_processor_process_capture(
    handle: i64,
    data: *mut f32,
    num_channels: u32,
    samples_per_channel: u32,
) -> i32 {
    if data.is_null() {
        return -5; // NullPointer
    }
    with_instance(handle, |inst| {
        let total = (num_channels * samples_per_channel) as usize;
        let slice = unsafe { std::slice::from_raw_parts_mut(data, total) };
        let mut channels: Vec<&mut [f32]> = slice
            .chunks_mut(samples_per_channel as usize)
            .collect();
        match inst.processor.process_capture_frame(channels.iter_mut().map(|c| &mut **c)) {
            Ok(()) => 0,
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
    with_instance(handle, |inst| {
        let total = (num_channels * samples_per_channel) as usize;
        let slice = unsafe { std::slice::from_raw_parts_mut(data, total) };
        let mut channels: Vec<&mut [f32]> = slice
            .chunks_mut(samples_per_channel as usize)
            .collect();
        match inst.processor.process_render_frame(channels.iter_mut().map(|c| &mut **c)) {
            Ok(()) => 0,
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
    with_instance(handle, |inst| {
        let total = (num_channels * samples_per_channel) as usize;
        let slice = unsafe { std::slice::from_raw_parts(data, total) };
        let channels: Vec<&[f32]> = slice
            .chunks(samples_per_channel as usize)
            .collect();
        match inst.processor.analyze_render_frame(channels.into_iter()) {
            Ok(()) => 0,
            Err(e) => error_to_code(&e),
        }
    })
}
```

- [ ] **Step 4: Add config setter functions to `native/src/lib.rs`**

Append to `lib.rs`:

```rust
use config::*;

#[no_mangle]
pub extern "C" fn apm_set_pipeline(
    handle: i64,
    max_processing_rate: i32,
    multi_ch_render: bool,
    multi_ch_capture: bool,
    downmix_method: i32,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.pipeline = Pipeline {
            maximum_internal_processing_rate: match max_processing_rate {
                32000 => PipelineProcessingRate::Max32000Hz,
                _ => PipelineProcessingRate::Max48000Hz,
            },
            multi_channel_render: multi_ch_render,
            multi_channel_capture: multi_ch_capture,
            capture_downmix_method: match downmix_method {
                1 => DownmixMethod::UseFirstChannel,
                _ => DownmixMethod::Average,
            },
        };
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_high_pass_filter(
    handle: i64,
    enabled: bool,
    apply_in_full_band: bool,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.high_pass_filter = if enabled {
            Some(HighPassFilter { apply_in_full_band })
        } else {
            None
        };
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_echo_canceller(
    handle: i64,
    mode: i32,
    stream_delay_ms: i32,
) -> i32 {
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
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_noise_suppression(
    handle: i64,
    enabled: bool,
    level: i32,
    analyze_linear_aec_output: bool,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.noise_suppression = if enabled {
            Some(NoiseSuppression {
                level: match level {
                    0 => NoiseSuppressionLevel::Low,
                    2 => NoiseSuppressionLevel::High,
                    3 => NoiseSuppressionLevel::VeryHigh,
                    _ => NoiseSuppressionLevel::Moderate,
                },
                analyze_linear_aec_output,
            })
        } else {
            None
        };
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_pre_amplifier(
    handle: i64,
    enabled: bool,
    fixed_gain_factor: f32,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.capture_amplifier = if enabled {
            Some(CaptureAmplifier::PreAmplifier(PreAmplifier {
                fixed_gain_factor,
            }))
        } else {
            None
        };
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_capture_level_adjustment(
    handle: i64,
    enabled: bool,
    pre_gain: f32,
    post_gain: f32,
    mic_emu_enabled: bool,
    mic_emu_initial: u8,
) -> i32 {
    with_instance(handle, |inst| {
        inst.config.capture_amplifier = if enabled {
            Some(CaptureAmplifier::CaptureLevelAdjustment(
                CaptureLevelAdjustment {
                    pre_gain_factor: pre_gain,
                    post_gain_factor: post_gain,
                    analog_mic_gain_emulation: if mic_emu_enabled {
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
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_gain_controller1(
    handle: i64,
    enabled: bool,
    mode: i32,
    target_level_dbfs: u8,
    compression_gain_db: u8,
    enable_limiter: bool,
) -> i32 {
    with_instance(handle, |inst| {
        if enabled {
            let gc1 = GainController1 {
                mode: match mode {
                    0 => GainControllerMode::AdaptiveAnalog,
                    1 => GainControllerMode::AdaptiveDigital,
                    _ => GainControllerMode::FixedDigital,
                },
                target_level_dbfs,
                compression_gain_db,
                enable_limiter,
                analog_gain_controller: None,
            };
            inst.config.gain_controller = Some(GainController::GainController1(gc1));
        } else {
            inst.config.gain_controller = None;
        }
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_analog_gain_controller(
    handle: i64,
    enabled: bool,
    startup_min_vol: i32,
    clipped_level_min: i32,
    enable_digital_adaptive: bool,
    clipped_level_step: i32,
    clipped_ratio_threshold: f32,
    clipped_wait_frames: i32,
) -> i32 {
    with_instance(handle, |inst| {
        if let Some(GainController::GainController1(ref mut gc1)) = inst.config.gain_controller {
            gc1.analog_gain_controller = if enabled {
                Some(AnalogGainController {
                    startup_min_volume: startup_min_vol,
                    clipped_level_min,
                    enable_digital_adaptive,
                    clipped_level_step,
                    clipped_ratio_threshold,
                    clipped_wait_frames,
                    clipping_predictor: None,
                })
            } else {
                None
            };
            inst.processor.set_config(inst.config);
            0
        } else {
            -6 // BadParameter — GainController1 not active
        }
    })
}

#[no_mangle]
pub extern "C" fn apm_set_clipping_predictor(
    handle: i64,
    enabled: bool,
    mode: i32,
    window_length: i32,
    ref_window_length: i32,
    ref_window_delay: i32,
    clipping_threshold: f32,
    crest_factor_margin: f32,
    use_predicted_step: bool,
) -> i32 {
    with_instance(handle, |inst| {
        if let Some(GainController::GainController1(ref mut gc1)) = inst.config.gain_controller {
            if let Some(ref mut agc) = gc1.analog_gain_controller {
                agc.clipping_predictor = if enabled {
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
                        use_predicted_step,
                    })
                } else {
                    None
                };
                inst.processor.set_config(inst.config);
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
    enabled: bool,
    input_vol_controller: bool,
    fixed_digital_gain_db: f32,
) -> i32 {
    with_instance(handle, |inst| {
        if enabled {
            let gc2 = GainController2 {
                input_volume_controller_enabled: input_vol_controller,
                adaptive_digital: None,
                fixed_digital: FixedDigital {
                    gain_db: fixed_digital_gain_db,
                },
            };
            inst.config.gain_controller = Some(GainController::GainController2(gc2));
        } else {
            inst.config.gain_controller = None;
        }
        inst.processor.set_config(inst.config);
        0
    })
}

#[no_mangle]
pub extern "C" fn apm_set_adaptive_digital(
    handle: i64,
    enabled: bool,
    headroom_db: f32,
    max_gain_db: f32,
    initial_gain_db: f32,
    max_gain_change_db_per_sec: f32,
    max_output_noise_level_dbfs: f32,
) -> i32 {
    with_instance(handle, |inst| {
        if let Some(GainController::GainController2(ref mut gc2)) = inst.config.gain_controller {
            gc2.adaptive_digital = if enabled {
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
            inst.processor.set_config(inst.config);
            0
        } else {
            -6
        }
    })
}
```

- [ ] **Step 5: Add stats and hint functions to `native/src/lib.rs`**

Append to `lib.rs`:

```rust
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
    with_instance(handle, |inst| {
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
pub extern "C" fn apm_processor_set_output_will_be_muted(handle: i64, muted: bool) {
    with_instance(handle, |inst| {
        inst.processor.set_output_will_be_muted(muted);
    });
}

#[no_mangle]
pub extern "C" fn apm_processor_set_stream_key_pressed(handle: i64, pressed: bool) {
    with_instance(handle, |inst| {
        inst.processor.set_stream_key_pressed(pressed);
    });
}
```

- [ ] **Step 6: Build the native library**

Run: `cd /Users/manhha/Developer/madeformed/webrtc-audio-processing-java/native && cargo build --release`

Expected: Compiles successfully. `target/release/libapm.dylib` (macOS) or `target/release/libapm.so` (Linux) is created.

- [ ] **Step 7: Commit**

```bash
git add native/
git commit -m "feat: add Rust FFI bridge for webrtc-audio-processing"
```

---

### Task 3: Java Error Types and Enums

**Files:**
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ErrorCode.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ProcessorException.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/PipelineProcessingRate.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/DownmixMethod.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NoiseSuppressionLevel.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/GainControllerMode.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ClippingPredictorMode.java`

- [ ] **Step 1: Create `ErrorCode.java`**

```java
package com.manhhavu.webrtc.audio;

public enum ErrorCode {
    UNSPECIFIED(-1),
    INITIALIZATION_FAILED(-2),
    UNSUPPORTED_COMPONENT(-3),
    UNSUPPORTED_FUNCTION(-4),
    NULL_POINTER(-5),
    BAD_PARAMETER(-6),
    BAD_SAMPLE_RATE(-7),
    BAD_DATA_LENGTH(-8),
    BAD_NUMBER_CHANNELS(-9),
    FILE_ERROR(-10),
    STREAM_PARAMETER_NOT_SET(-11),
    NOT_ENABLED(-12);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) return ec;
        }
        return UNSPECIFIED;
    }
}
```

- [ ] **Step 2: Create `ProcessorException.java`**

```java
package com.manhhavu.webrtc.audio;

public class ProcessorException extends RuntimeException {
    private final ErrorCode errorCode;

    public ProcessorException(ErrorCode errorCode) {
        super("WebRTC audio processing error: " + errorCode.name());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    static void checkResult(int result) {
        if (result < 0) {
            throw new ProcessorException(ErrorCode.fromCode(result));
        }
    }

    static void checkHandle(long handle) {
        if (handle <= 0) {
            throw new ProcessorException(ErrorCode.fromCode((int) handle));
        }
    }
}
```

- [ ] **Step 3: Create all enum files**

`PipelineProcessingRate.java`:
```java
package com.manhhavu.webrtc.audio;

public enum PipelineProcessingRate {
    MAX_32000_HZ(32000),
    MAX_48000_HZ(48000);

    private final int value;

    PipelineProcessingRate(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```

`DownmixMethod.java`:
```java
package com.manhhavu.webrtc.audio;

public enum DownmixMethod {
    AVERAGE(0),
    USE_FIRST_CHANNEL(1);

    private final int value;

    DownmixMethod(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```

`NoiseSuppressionLevel.java`:
```java
package com.manhhavu.webrtc.audio;

public enum NoiseSuppressionLevel {
    LOW(0),
    MODERATE(1),
    HIGH(2),
    VERY_HIGH(3);

    private final int value;

    NoiseSuppressionLevel(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```

`GainControllerMode.java`:
```java
package com.manhhavu.webrtc.audio;

public enum GainControllerMode {
    ADAPTIVE_ANALOG(0),
    ADAPTIVE_DIGITAL(1),
    FIXED_DIGITAL(2);

    private final int value;

    GainControllerMode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```

`ClippingPredictorMode.java`:
```java
package com.manhhavu.webrtc.audio;

public enum ClippingPredictorMode {
    CLIPPING_EVENT_PREDICTION(0),
    ADAPTIVE_STEP_CLIPPING_PEAK_PREDICTION(1),
    FIXED_STEP_CLIPPING_PEAK_PREDICTION(2);

    private final int value;

    ClippingPredictorMode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :webrtc-audio-processing:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ErrorCode.java \
  webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ProcessorException.java \
  webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/PipelineProcessingRate.java \
  webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/DownmixMethod.java \
  webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NoiseSuppressionLevel.java \
  webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/GainControllerMode.java \
  webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ClippingPredictorMode.java
git commit -m "feat: add error types and config enums"
```

---

### Task 4: Java Config Records

**Files:**
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Pipeline.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/HighPassFilter.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/EchoCanceller.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NoiseSuppression.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/AnalogMicGainEmulation.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/CaptureAmplifier.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/ClippingPredictor.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/AnalogGainController.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/AdaptiveDigital.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/FixedDigital.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/GainController.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Stats.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Config.java`

- [ ] **Step 1: Create simple config records**

`Pipeline.java`:
```java
package com.manhhavu.webrtc.audio;

public record Pipeline(
        PipelineProcessingRate maximumInternalProcessingRate,
        boolean multiChannelRender,
        boolean multiChannelCapture,
        DownmixMethod captureDownmixMethod) {

    public static Pipeline defaults() {
        return new Pipeline(PipelineProcessingRate.MAX_48000_HZ, false, false, DownmixMethod.AVERAGE);
    }
}
```

`HighPassFilter.java`:
```java
package com.manhhavu.webrtc.audio;

public record HighPassFilter(boolean applyInFullBand) {}
```

`NoiseSuppression.java`:
```java
package com.manhhavu.webrtc.audio;

public record NoiseSuppression(NoiseSuppressionLevel level, boolean analyzeLinearAecOutput) {}
```

`AnalogMicGainEmulation.java`:
```java
package com.manhhavu.webrtc.audio;

public record AnalogMicGainEmulation(int initialLevel) {}
```

`AdaptiveDigital.java`:
```java
package com.manhhavu.webrtc.audio;

public record AdaptiveDigital(
        float headroomDb,
        float maxGainDb,
        float initialGainDb,
        float maxGainChangeDbPerSecond,
        float maxOutputNoiseLevelDbfs) {}
```

`FixedDigital.java`:
```java
package com.manhhavu.webrtc.audio;

public record FixedDigital(float gainDb) {}
```

`ClippingPredictor.java`:
```java
package com.manhhavu.webrtc.audio;

public record ClippingPredictor(
        ClippingPredictorMode mode,
        int windowLength,
        int referenceWindowLength,
        int referenceWindowDelay,
        float clippingThreshold,
        float crestFactorMargin,
        boolean usePredictedStep) {}
```

`AnalogGainController.java`:
```java
package com.manhhavu.webrtc.audio;

public record AnalogGainController(
        int startupMinVolume,
        int clippedLevelMin,
        boolean enableDigitalAdaptive,
        int clippedLevelStep,
        float clippedRatioThreshold,
        int clippedWaitFrames,
        ClippingPredictor clippingPredictor) {}
```

- [ ] **Step 2: Create sealed interfaces**

`EchoCanceller.java`:
```java
package com.manhhavu.webrtc.audio;

public sealed interface EchoCanceller {
    record Mobile(int streamDelayMs) implements EchoCanceller {}
    record Full(Integer streamDelayMs) implements EchoCanceller {}
}
```

`CaptureAmplifier.java`:
```java
package com.manhhavu.webrtc.audio;

public sealed interface CaptureAmplifier {
    record PreAmplifier(float fixedGainFactor) implements CaptureAmplifier {}
    record CaptureLevelAdjustment(
            float preGainFactor,
            float postGainFactor,
            AnalogMicGainEmulation analogMicGainEmulation) implements CaptureAmplifier {}
}
```

`GainController.java`:
```java
package com.manhhavu.webrtc.audio;

public sealed interface GainController {
    record GainController1(
            GainControllerMode mode,
            int targetLevelDbfs,
            int compressionGainDb,
            boolean enableLimiter,
            AnalogGainController analogGainController) implements GainController {}

    record GainController2(
            boolean inputVolumeControllerEnabled,
            AdaptiveDigital adaptiveDigital,
            FixedDigital fixedDigital) implements GainController {}
}
```

- [ ] **Step 3: Create Stats and Config**

`Stats.java`:
```java
package com.manhhavu.webrtc.audio;

public record Stats(
        Boolean voiceDetected,
        Double echoReturnLoss,
        Double echoReturnLossEnhancement,
        Double divergentFilterFraction,
        Integer delayMedianMs,
        Integer delayStandardDeviationMs,
        Double residualEchoLikelihood,
        Double residualEchoLikelihoodRecentMax,
        Integer delayMs) {}
```

`Config.java`:
```java
package com.manhhavu.webrtc.audio;

public record Config(
        Pipeline pipeline,
        CaptureAmplifier captureAmplifier,
        HighPassFilter highPassFilter,
        EchoCanceller echoCanceller,
        NoiseSuppression noiseSuppression,
        GainController gainController) {

    public static Config defaults() {
        return new Config(Pipeline.defaults(), null, null, null, null, null);
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :webrtc-audio-processing:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/
git commit -m "feat: add config records, sealed interfaces, and Stats"
```

---

### Task 5: JNA Binding and Native Library Loader

**Files:**
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NativeLib.java`
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NativeLibLoader.java`

- [ ] **Step 1: Create `NativeLib.java`**

```java
package com.manhhavu.webrtc.audio;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

interface NativeLib extends Library {
    // Lifecycle
    long apm_processor_new(int sample_rate_hz);
    void apm_processor_destroy(long handle);
    void apm_processor_reinitialize(long handle);
    int apm_processor_num_samples_per_frame(long handle);

    // Processing
    int apm_processor_process_capture(long handle, float[] data, int num_channels, int samples_per_channel);
    int apm_processor_process_render(long handle, float[] data, int num_channels, int samples_per_channel);
    int apm_processor_analyze_render(long handle, float[] data, int num_channels, int samples_per_channel);

    // Config setters
    int apm_set_pipeline(long handle, int max_processing_rate, boolean multi_ch_render, boolean multi_ch_capture, int downmix_method);
    int apm_set_high_pass_filter(long handle, boolean enabled, boolean apply_in_full_band);
    int apm_set_echo_canceller(long handle, int mode, int stream_delay_ms);
    int apm_set_noise_suppression(long handle, boolean enabled, int level, boolean analyze_linear_aec_output);
    int apm_set_pre_amplifier(long handle, boolean enabled, float fixed_gain_factor);
    int apm_set_capture_level_adjustment(long handle, boolean enabled, float pre_gain, float post_gain, boolean mic_emu_enabled, byte mic_emu_initial);
    int apm_set_gain_controller1(long handle, boolean enabled, int mode, byte target_level_dbfs, byte compression_gain_db, boolean enable_limiter);
    int apm_set_analog_gain_controller(long handle, boolean enabled, int startup_min_vol, int clipped_level_min, boolean enable_digital_adaptive, int clipped_level_step, float clipped_ratio_threshold, int clipped_wait_frames);
    int apm_set_clipping_predictor(long handle, boolean enabled, int mode, int window_length, int ref_window_length, int ref_window_delay, float clipping_threshold, float crest_factor_margin, boolean use_predicted_step);
    int apm_set_gain_controller2(long handle, boolean enabled, boolean input_vol_controller, float fixed_digital_gain_db);
    int apm_set_adaptive_digital(long handle, boolean enabled, float headroom_db, float max_gain_db, float initial_gain_db, float max_gain_change_db_per_sec, float max_output_noise_level_dbfs);

    // Stats & hints
    int apm_processor_get_stats(long handle,
                                byte[] out_voice_detected,
                                double[] out_erl,
                                double[] out_erle,
                                double[] out_divergent_filter_fraction,
                                int[] out_delay_median_ms,
                                int[] out_delay_std_ms,
                                double[] out_residual_echo_likelihood,
                                double[] out_residual_echo_likelihood_recent_max,
                                int[] out_delay_ms);
    void apm_processor_set_output_will_be_muted(long handle, boolean muted);
    void apm_processor_set_stream_key_pressed(long handle, boolean pressed);
}
```

- [ ] **Step 2: Create `NativeLibLoader.java`**

```java
package com.manhhavu.webrtc.audio;

import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class NativeLibLoader {
    private static volatile NativeLib instance;

    static NativeLib load() {
        if (instance == null) {
            synchronized (NativeLibLoader.class) {
                if (instance == null) {
                    instance = doLoad();
                }
            }
        }
        return instance;
    }

    private static NativeLib doLoad() {
        String platform = detectPlatform();
        String libName = libFileName();
        String resourcePath = "/native/" + platform + "/" + libName;

        // Try classpath extraction first
        try (InputStream is = NativeLibLoader.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Path tempDir = Files.createTempDirectory("webrtc-apm-");
                Path tempLib = tempDir.resolve(libName);
                Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
                tempLib.toFile().deleteOnExit();
                tempDir.toFile().deleteOnExit();
                return Native.load(tempLib.toAbsolutePath().toString(), NativeLib.class);
            }
        } catch (IOException e) {
            // Fall through to java.library.path
        }

        // Fallback: try system library path
        try {
            return Native.load("apm", NativeLib.class);
        } catch (UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError(
                    "Failed to load native library 'apm'. " +
                    "Platform: " + platform + ". " +
                    "Tried classpath resource: " + resourcePath + " and java.library.path. " +
                    "Ensure webrtc-audio-processing-natives is on the classpath for your platform, " +
                    "or set java.library.path to the directory containing " + libName + ".");
        }
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        if (os.contains("mac")) {
            return "aarch64".equals(arch) ? "macos-aarch64" : "macos-x86_64";
        } else if (os.contains("linux")) {
            return "amd64".equals(arch) || "x86_64".equals(arch) ? "linux-x86_64" : "linux-aarch64";
        }
        throw new UnsupportedOperationException("Unsupported platform: " + os + " " + arch);
    }

    private static String libFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") ? "libapm.dylib" : "libapm.so";
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :webrtc-audio-processing:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NativeLib.java \
  webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/NativeLibLoader.java
git commit -m "feat: add JNA native library interface and platform-aware loader"
```

---

### Task 6: Processor Class

**Files:**
- Create: `webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Processor.java`

- [ ] **Step 1: Create `Processor.java`**

```java
package com.manhhavu.webrtc.audio;

public class Processor implements AutoCloseable {
    private final NativeLib lib;
    private final long handle;
    private volatile boolean closed;

    public Processor(int sampleRateHz) {
        this.lib = NativeLibLoader.load();
        this.handle = lib.apm_processor_new(sampleRateHz);
        ProcessorException.checkHandle(handle);
        this.closed = false;
    }

    public void setConfig(Config config) {
        ensureOpen();
        applyPipeline(config.pipeline());
        applyHighPassFilter(config.highPassFilter());
        applyEchoCanceller(config.echoCanceller());
        applyNoiseSuppression(config.noiseSuppression());
        applyCaptureAmplifier(config.captureAmplifier());
        applyGainController(config.gainController());
    }

    public void reinitialize() {
        ensureOpen();
        lib.apm_processor_reinitialize(handle);
    }

    public void processCaptureFrame(float[] data) {
        ensureOpen();
        ProcessorException.checkResult(
                lib.apm_processor_process_capture(handle, data, 1, data.length));
    }

    public void processCaptureFrame(float[][] data) {
        ensureOpen();
        float[] flat = flatten(data);
        ProcessorException.checkResult(
                lib.apm_processor_process_capture(handle, flat, data.length, data[0].length));
        unflatten(flat, data);
    }

    public void processRenderFrame(float[] data) {
        ensureOpen();
        ProcessorException.checkResult(
                lib.apm_processor_process_render(handle, data, 1, data.length));
    }

    public void processRenderFrame(float[][] data) {
        ensureOpen();
        float[] flat = flatten(data);
        ProcessorException.checkResult(
                lib.apm_processor_process_render(handle, flat, data.length, data[0].length));
        unflatten(flat, data);
    }

    public void analyzeRenderFrame(float[] data) {
        ensureOpen();
        ProcessorException.checkResult(
                lib.apm_processor_analyze_render(handle, data, 1, data.length));
    }

    public void analyzeRenderFrame(float[][] data) {
        ensureOpen();
        float[] flat = flatten(data);
        ProcessorException.checkResult(
                lib.apm_processor_analyze_render(handle, flat, data.length, data[0].length));
    }

    public void setOutputWillBeMuted(boolean muted) {
        ensureOpen();
        lib.apm_processor_set_output_will_be_muted(handle, muted);
    }

    public void setStreamKeyPressed(boolean pressed) {
        ensureOpen();
        lib.apm_processor_set_stream_key_pressed(handle, pressed);
    }

    public Stats getStats() {
        ensureOpen();
        byte[] voiceDetected = new byte[1];
        double[] erl = new double[1];
        double[] erle = new double[1];
        double[] divergent = new double[1];
        int[] delayMedian = new int[1];
        int[] delayStd = new int[1];
        double[] residual = new double[1];
        double[] residualMax = new double[1];
        int[] delay = new int[1];

        ProcessorException.checkResult(lib.apm_processor_get_stats(
                handle, voiceDetected, erl, erle, divergent,
                delayMedian, delayStd, residual, residualMax, delay));

        return new Stats(
                voiceDetected[0] == -1 ? null : voiceDetected[0] == 1,
                Double.isNaN(erl[0]) ? null : erl[0],
                Double.isNaN(erle[0]) ? null : erle[0],
                Double.isNaN(divergent[0]) ? null : divergent[0],
                delayMedian[0] == -1 ? null : delayMedian[0],
                delayStd[0] == -1 ? null : delayStd[0],
                Double.isNaN(residual[0]) ? null : residual[0],
                Double.isNaN(residualMax[0]) ? null : residualMax[0],
                delay[0] == -1 ? null : delay[0]);
    }

    public int numSamplesPerFrame() {
        ensureOpen();
        return lib.apm_processor_num_samples_per_frame(handle);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            lib.apm_processor_destroy(handle);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Processor is closed");
        }
    }

    private void applyPipeline(Pipeline p) {
        if (p == null) return;
        ProcessorException.checkResult(lib.apm_set_pipeline(
                handle,
                p.maximumInternalProcessingRate().value(),
                p.multiChannelRender(),
                p.multiChannelCapture(),
                p.captureDownmixMethod().value()));
    }

    private void applyHighPassFilter(HighPassFilter hpf) {
        ProcessorException.checkResult(lib.apm_set_high_pass_filter(
                handle, hpf != null, hpf != null && hpf.applyInFullBand()));
    }

    private void applyEchoCanceller(EchoCanceller ec) {
        if (ec == null) {
            ProcessorException.checkResult(lib.apm_set_echo_canceller(handle, 0, 0));
        } else if (ec instanceof EchoCanceller.Mobile m) {
            ProcessorException.checkResult(lib.apm_set_echo_canceller(handle, 1, m.streamDelayMs()));
        } else if (ec instanceof EchoCanceller.Full f) {
            ProcessorException.checkResult(lib.apm_set_echo_canceller(
                    handle, 2, f.streamDelayMs() != null ? f.streamDelayMs() : -1));
        }
    }

    private void applyNoiseSuppression(NoiseSuppression ns) {
        ProcessorException.checkResult(lib.apm_set_noise_suppression(
                handle, ns != null,
                ns != null ? ns.level().value() : 1,
                ns != null && ns.analyzeLinearAecOutput()));
    }

    private void applyCaptureAmplifier(CaptureAmplifier ca) {
        if (ca == null) {
            ProcessorException.checkResult(lib.apm_set_pre_amplifier(handle, false, 1.0f));
        } else if (ca instanceof CaptureAmplifier.PreAmplifier pa) {
            ProcessorException.checkResult(lib.apm_set_pre_amplifier(handle, true, pa.fixedGainFactor()));
        } else if (ca instanceof CaptureAmplifier.CaptureLevelAdjustment cla) {
            var emu = cla.analogMicGainEmulation();
            ProcessorException.checkResult(lib.apm_set_capture_level_adjustment(
                    handle, true, cla.preGainFactor(), cla.postGainFactor(),
                    emu != null, emu != null ? (byte) emu.initialLevel() : (byte) 255));
        }
    }

    private void applyGainController(GainController gc) {
        if (gc == null) {
            ProcessorException.checkResult(lib.apm_set_gain_controller1(
                    handle, false, 2, (byte) 3, (byte) 9, true));
            return;
        }
        if (gc instanceof GainController.GainController1 gc1) {
            ProcessorException.checkResult(lib.apm_set_gain_controller1(
                    handle, true, gc1.mode().value(),
                    (byte) gc1.targetLevelDbfs(), (byte) gc1.compressionGainDb(),
                    gc1.enableLimiter()));
            var agc = gc1.analogGainController();
            if (agc != null) {
                ProcessorException.checkResult(lib.apm_set_analog_gain_controller(
                        handle, true, agc.startupMinVolume(), agc.clippedLevelMin(),
                        agc.enableDigitalAdaptive(), agc.clippedLevelStep(),
                        agc.clippedRatioThreshold(), agc.clippedWaitFrames()));
                var cp = agc.clippingPredictor();
                if (cp != null) {
                    ProcessorException.checkResult(lib.apm_set_clipping_predictor(
                            handle, true, cp.mode().value(), cp.windowLength(),
                            cp.referenceWindowLength(), cp.referenceWindowDelay(),
                            cp.clippingThreshold(), cp.crestFactorMargin(),
                            cp.usePredictedStep()));
                }
            }
        } else if (gc instanceof GainController.GainController2 gc2) {
            ProcessorException.checkResult(lib.apm_set_gain_controller2(
                    handle, true, gc2.inputVolumeControllerEnabled(),
                    gc2.fixedDigital() != null ? gc2.fixedDigital().gainDb() : 0.0f));
            var ad = gc2.adaptiveDigital();
            if (ad != null) {
                ProcessorException.checkResult(lib.apm_set_adaptive_digital(
                        handle, true, ad.headroomDb(), ad.maxGainDb(), ad.initialGainDb(),
                        ad.maxGainChangeDbPerSecond(), ad.maxOutputNoiseLevelDbfs()));
            }
        }
    }

    private static float[] flatten(float[][] channels) {
        int numChannels = channels.length;
        int samplesPerChannel = channels[0].length;
        float[] flat = new float[numChannels * samplesPerChannel];
        for (int ch = 0; ch < numChannels; ch++) {
            System.arraycopy(channels[ch], 0, flat, ch * samplesPerChannel, samplesPerChannel);
        }
        return flat;
    }

    private static void unflatten(float[] flat, float[][] channels) {
        int samplesPerChannel = channels[0].length;
        for (int ch = 0; ch < channels.length; ch++) {
            System.arraycopy(flat, ch * samplesPerChannel, channels[ch], 0, samplesPerChannel);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :webrtc-audio-processing:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add webrtc-audio-processing/src/main/java/com/manhhavu/webrtc/audio/Processor.java
git commit -m "feat: add Processor class with full config and processing support"
```

---

### Task 7: Integration Tests

**Files:**
- Create: `webrtc-audio-processing/src/test/java/com/manhhavu/webrtc/audio/ProcessorTest.java`
- Create: `webrtc-audio-processing/src/test/java/com/manhhavu/webrtc/audio/ConfigTest.java`

Integration tests require the native library. They check for the `APM_LIB_PATH` environment variable or the native lib on classpath; if unavailable, tests are skipped.

- [ ] **Step 1: Create `ProcessorTest.java`**

```java
package com.manhhavu.webrtc.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APM_LIB_PATH", matches = ".+")
class ProcessorTest {

    private Processor processor;

    @BeforeEach
    void setUp() {
        System.setProperty("jna.library.path", System.getenv("APM_LIB_PATH"));
        processor = new Processor(48_000);
    }

    @AfterEach
    void tearDown() {
        if (processor != null) {
            processor.close();
        }
    }

    @Test
    void createAndDestroy() {
        assertNotNull(processor);
        assertEquals(480, processor.numSamplesPerFrame());
    }

    @Test
    void processSilence() {
        float[] capture = new float[480];
        float[] render = new float[480];

        processor.analyzeRenderFrame(render);
        processor.processCaptureFrame(capture);

        // Silence in, silence out
        for (float sample : capture) {
            assertEquals(0.0f, sample, 1e-6f);
        }
    }

    @Test
    void getStatsAfterProcessing() {
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);

        Stats stats = processor.getStats();
        assertNotNull(stats);
    }

    @Test
    void processWithEchoCanceller() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Full(null),
                null, null));

        float[] render = new float[480];
        float[] capture = new float[480];

        // Fill render with a tone
        for (int i = 0; i < render.length; i++) {
            render[i] = (float) Math.sin(2 * Math.PI * 440 * i / 48000.0);
        }
        // Capture has echo of the render
        System.arraycopy(render, 0, capture, 0, render.length);

        processor.analyzeRenderFrame(render);
        processor.processCaptureFrame(capture);

        // After AEC, captured echo should be suppressed
        // (may take multiple frames to converge, just verify no crash)
    }

    @Test
    void processMultiChannel() {
        processor.setConfig(new Config(
                new Pipeline(PipelineProcessingRate.MAX_48000_HZ, true, true, DownmixMethod.AVERAGE),
                null, null, null, null, null));

        float[][] stereoCapture = new float[2][480];
        float[][] stereoRender = new float[2][480];

        processor.analyzeRenderFrame(stereoRender);
        processor.processCaptureFrame(stereoCapture);
    }

    @Test
    void reinitialize() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Full(null),
                null, null));

        processor.reinitialize();

        // Should still work after reinitialize
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void closedProcessorThrows() {
        processor.close();
        assertThrows(IllegalStateException.class, () -> processor.processCaptureFrame(new float[480]));
    }

    @Test
    void setOutputWillBeMuted() {
        processor.setOutputWillBeMuted(true);
        processor.setOutputWillBeMuted(false);
        // No exception means success
    }

    @Test
    void setStreamKeyPressed() {
        processor.setStreamKeyPressed(true);
        processor.setStreamKeyPressed(false);
    }

    @Test
    void differentSampleRates() {
        try (var p8k = new Processor(8_000)) {
            assertEquals(80, p8k.numSamplesPerFrame());
            float[] frame = new float[80];
            p8k.processCaptureFrame(frame);
        }
        try (var p16k = new Processor(16_000)) {
            assertEquals(160, p16k.numSamplesPerFrame());
            float[] frame = new float[160];
            p16k.processCaptureFrame(frame);
        }
    }
}
```

- [ ] **Step 2: Create `ConfigTest.java`**

```java
package com.manhhavu.webrtc.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APM_LIB_PATH", matches = ".+")
class ConfigTest {

    private Processor processor;

    @BeforeEach
    void setUp() {
        System.setProperty("jna.library.path", System.getenv("APM_LIB_PATH"));
        processor = new Processor(48_000);
    }

    @AfterEach
    void tearDown() {
        if (processor != null) {
            processor.close();
        }
    }

    @Test
    void defaultConfig() {
        processor.setConfig(Config.defaults());
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void echoCancellerMobile() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Mobile(10),
                null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void echoCancellerFull() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Full(null),
                null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void noiseSuppression() {
        for (NoiseSuppressionLevel level : NoiseSuppressionLevel.values()) {
            processor.setConfig(new Config(
                    Pipeline.defaults(), null, null, null,
                    new NoiseSuppression(level, false),
                    null));
            float[] frame = new float[480];
            processor.processCaptureFrame(frame);
        }
    }

    @Test
    void highPassFilter() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null,
                new HighPassFilter(true),
                null, null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void preAmplifier() {
        processor.setConfig(new Config(
                Pipeline.defaults(),
                new CaptureAmplifier.PreAmplifier(2.0f),
                null, null, null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void captureLevelAdjustment() {
        processor.setConfig(new Config(
                Pipeline.defaults(),
                new CaptureAmplifier.CaptureLevelAdjustment(1.5f, 1.2f,
                        new AnalogMicGainEmulation(200)),
                null, null, null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void gainController1() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null, null, null,
                new GainController.GainController1(
                        GainControllerMode.FIXED_DIGITAL, 3, 9, true, null)));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void gainController2() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null, null, null,
                new GainController.GainController2(false,
                        new AdaptiveDigital(5.0f, 50.0f, 15.0f, 6.0f, -50.0f),
                        new FixedDigital(0.0f))));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void fullConfig() {
        processor.setConfig(new Config(
                new Pipeline(PipelineProcessingRate.MAX_48000_HZ, false, false, DownmixMethod.AVERAGE),
                new CaptureAmplifier.PreAmplifier(1.0f),
                new HighPassFilter(true),
                new EchoCanceller.Full(null),
                new NoiseSuppression(NoiseSuppressionLevel.HIGH, false),
                new GainController.GainController1(
                        GainControllerMode.ADAPTIVE_DIGITAL, 3, 9, true,
                        new AnalogGainController(0, 70, true, 15, 0.1f, 300,
                                new ClippingPredictor(ClippingPredictorMode.CLIPPING_EVENT_PREDICTION,
                                        5, 5, 5, -1.0f, 3.0f, true)))));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }
}
```

- [ ] **Step 3: Verify tests compile**

Run: `./gradlew :webrtc-audio-processing:compileTestJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Build native and run tests**

Run:
```bash
cd native && cargo build --release && cd ..
APM_LIB_PATH=$(pwd)/native/target/release ./gradlew :webrtc-audio-processing:test
```

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add webrtc-audio-processing/src/test/
git commit -m "feat: add integration tests for Processor and Config"
```

---

### Task 8: GitHub Actions CI

**Files:**
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Create `.github/workflows/build.yml`**

```yaml
name: Build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    strategy:
      matrix:
        include:
          - os: macos-latest
            target: macos-aarch64
            lib: libapm.dylib
          - os: ubuntu-latest
            target: linux-x86_64
            lib: libapm.so
    runs-on: ${{ matrix.os }}

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Checkout webrtc-audio-processing
        uses: actions/checkout@v4
        with:
          repository: niclas-aspect/webrtc-audio-processing
          path: webrtc-audio-processing-crate
          submodules: recursive

      - name: Install Rust toolchain
        uses: dtolnay/rust-toolchain@stable

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Adjust Cargo.toml paths for CI
        run: |
          sed -i.bak 's|path = "../../webrtc-audio-processing"|path = "../webrtc-audio-processing-crate"|' native/Cargo.toml
          sed -i.bak 's|path = "../../webrtc-audio-processing/webrtc-audio-processing-config"|path = "../webrtc-audio-processing-crate/webrtc-audio-processing-config"|' native/Cargo.toml

      - name: Build native library
        working-directory: native
        run: cargo build --release

      - name: Build Java
        run: ./gradlew build -x test

      - name: Run tests
        run: ./gradlew test
        env:
          APM_LIB_PATH: ${{ github.workspace }}/native/target/release

      - name: Upload native artifact
        uses: actions/upload-artifact@v4
        with:
          name: native-${{ matrix.target }}
          path: native/target/release/${{ matrix.lib }}

  package:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Download macOS native
        uses: actions/download-artifact@v4
        with:
          name: native-macos-aarch64
          path: webrtc-audio-processing-natives/build/natives/macos-aarch64

      - name: Download Linux native
        uses: actions/download-artifact@v4
        with:
          name: native-linux-x86_64
          path: webrtc-audio-processing-natives/build/natives/linux-x86_64

      - name: Package classifier JARs
        run: ./gradlew :webrtc-audio-processing-natives:assemble

      - name: Upload JARs
        uses: actions/upload-artifact@v4
        with:
          name: jars
          path: |
            webrtc-audio-processing/build/libs/*.jar
            webrtc-audio-processing-natives/build/libs/*.jar
```

Note: The `repository` for the webrtc-audio-processing checkout needs to match wherever the crate is hosted. Adjust `niclas-aspect/webrtc-audio-processing` to the actual GitHub repo. If it's a private fork, you'll need to add a token.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "feat: add GitHub Actions CI workflow"
```

---

### Task 9: End-to-End Verification

- [ ] **Step 1: Build everything locally**

Run:
```bash
cd native && cargo build --release && cd ..
./gradlew build -x test
APM_LIB_PATH=$(pwd)/native/target/release ./gradlew test
```

Expected: All tests pass.

- [ ] **Step 2: Package native JARs locally**

Run:
```bash
./gradlew :webrtc-audio-processing-natives:cargoReleaseBuild
./gradlew :webrtc-audio-processing-natives:assemble
ls -la webrtc-audio-processing-natives/build/libs/
```

Expected: Classifier JAR(s) for the current platform are created.

- [ ] **Step 3: Verify the API JAR has no native dependency**

Run:
```bash
jar tf webrtc-audio-processing/build/libs/webrtc-audio-processing-*.jar | head -20
```

Expected: Only `.class` files under `com/manhhavu/webrtc/audio/`, no `.dylib` or `.so`.

- [ ] **Step 4: Final commit if any adjustments were needed**

```bash
git add -A
git status
# Only commit if there are changes
git commit -m "fix: adjustments from end-to-end verification"
```
