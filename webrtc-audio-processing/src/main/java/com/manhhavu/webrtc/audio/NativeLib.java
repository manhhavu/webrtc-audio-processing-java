package com.manhhavu.webrtc.audio;

import com.sun.jna.Library;

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

    // Config setters (booleans passed as int: 0=false, 1=true for C ABI compatibility)
    int apm_set_pipeline(long handle, int max_processing_rate, int multi_ch_render, int multi_ch_capture, int downmix_method);
    int apm_set_high_pass_filter(long handle, int enabled, int apply_in_full_band);
    int apm_set_echo_canceller(long handle, int mode, int stream_delay_ms);
    int apm_set_noise_suppression(long handle, int enabled, int level, int analyze_linear_aec_output);
    int apm_set_pre_amplifier(long handle, int enabled, float fixed_gain_factor);
    int apm_set_capture_level_adjustment(long handle, int enabled, float pre_gain, float post_gain, int mic_emu_enabled, byte mic_emu_initial);
    int apm_set_gain_controller1(long handle, int enabled, int mode, byte target_level_dbfs, byte compression_gain_db, int enable_limiter);
    int apm_set_analog_gain_controller(long handle, int enabled, int startup_min_vol, int clipped_level_min, int enable_digital_adaptive, int clipped_level_step, float clipped_ratio_threshold, int clipped_wait_frames);
    int apm_set_clipping_predictor(long handle, int enabled, int mode, int window_length, int ref_window_length, int ref_window_delay, float clipping_threshold, float crest_factor_margin, int use_predicted_step);
    int apm_set_gain_controller2(long handle, int enabled, int input_vol_controller, float fixed_digital_gain_db);
    int apm_set_adaptive_digital(long handle, int enabled, float headroom_db, float max_gain_db, float initial_gain_db, float max_gain_change_db_per_sec, float max_output_noise_level_dbfs);
    int apm_apply_config(long handle);

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
    void apm_processor_set_output_will_be_muted(long handle, int muted);
    void apm_processor_set_stream_key_pressed(long handle, int pressed);
}
