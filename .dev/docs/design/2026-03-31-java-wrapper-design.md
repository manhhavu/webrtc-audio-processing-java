# WebRTC Audio Processing Java Wrapper — Design Spec

## Overview

A Java wrapper for the [webrtc-audio-processing](https://crates.io/crates/webrtc-audio-processing) Rust crate, published as a Maven/Gradle dependency. Developers add the dependency and get working audio processing (AEC, NS, AGC, VAD) with no native build steps.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| API fidelity | Full mirror of Rust API | All config options exposed |
| Native delivery | Classifier JARs per platform | Auto-select via os.name/os.arch; no wasted bytes |
| FFI approach | JNA | Simpler Rust FFI, proven in dev-svi-nova, negligible overhead at 10ms frames |
| Config passing | Per-group FFI functions | No JSON lib needed, self-documenting signatures |
| Language | Java (21+) | No Kotlin stdlib dep, broad consumer compatibility |
| Rust bridge location | In this repo (`native/`) | Self-contained; bridge is Java-specific |
| Build system | Gradle Kotlin DSL | Consistent with existing projects |
| Maven coordinates | `com.manhhavu:webrtc-audio-processing` | — |
| Initial platforms | macOS aarch64, Linux x86_64 | Dev + typical server deployment |
| Java version | 21+ (managed via mise) | Records, sealed interfaces |

## Project Structure

```
webrtc-audio-processing-java/
├── native/                              # Rust FFI bridge (cdylib)
│   ├── Cargo.toml
│   └── src/lib.rs
├── webrtc-audio-processing/             # Java API module
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/manhhavu/webrtc/audio/
│       │   ├── Processor.java
│       │   ├── Config.java
│       │   ├── Pipeline.java
│       │   ├── PipelineProcessingRate.java
│       │   ├── DownmixMethod.java
│       │   ├── EchoCanceller.java
│       │   ├── NoiseSuppression.java
│       │   ├── NoiseSuppressionLevel.java
│       │   ├── HighPassFilter.java
│       │   ├── CaptureAmplifier.java
│       │   ├── PreAmplifier.java
│       │   ├── CaptureLevelAdjustment.java
│       │   ├── AnalogMicGainEmulation.java
│       │   ├── GainController.java
│       │   ├── GainController1.java
│       │   ├── GainControllerMode.java
│       │   ├── AnalogGainController.java
│       │   ├── ClippingPredictor.java
│       │   ├── ClippingPredictorMode.java
│       │   ├── GainController2.java
│       │   ├── AdaptiveDigital.java
│       │   ├── FixedDigital.java
│       │   ├── Stats.java
│       │   ├── ProcessorException.java
│       │   ├── ErrorCode.java
│       │   └── NativeLib.java           # JNA interface (internal)
│       └── test/java/com/manhhavu/webrtc/audio/
│           ├── ProcessorTest.java
│           ├── EchoCancellerTest.java
│           ├── NoiseSuppressionTest.java
│           ├── MultiChannelTest.java
│           └── ConfigTest.java
├── webrtc-audio-processing-natives/     # Native lib packaging
│   └── build.gradle.kts
├── build.gradle.kts                     # Root build
├── settings.gradle.kts
├── .mise.toml                           # Java 21+
├── .github/
│   └── workflows/
│       └── build.yml
└── gradle/
```

## Rust FFI Bridge (`native/`)

Compiles to a `cdylib` (libapm.dylib / libapm.so). Depends on `webrtc-audio-processing` with the `bundled` feature.

### Functions

**Lifecycle:**

```c
int64_t apm_processor_new(uint32_t sample_rate_hz);     // returns handle or negative error
void    apm_processor_destroy(int64_t handle);
void    apm_processor_reinitialize(int64_t handle);
```

**Processing:**

```c
int32_t apm_processor_process_capture(int64_t handle, float* data, uint32_t num_channels, uint32_t samples_per_channel);
int32_t apm_processor_process_render(int64_t handle, float* data, uint32_t num_channels, uint32_t samples_per_channel);
int32_t apm_processor_analyze_render(int64_t handle, const float* data, uint32_t num_channels, uint32_t samples_per_channel);
```

Audio data is a flat float array: `[ch0_sample0, ch0_sample1, ..., ch1_sample0, ch1_sample1, ...]` (non-interleaved, channels concatenated). The Rust side slices it into per-channel `&mut [f32]`.

**Config — per-group setters:**

```c
int32_t apm_set_pipeline(int64_t handle, int32_t max_processing_rate, bool multi_ch_render, bool multi_ch_capture, int32_t downmix_method);
int32_t apm_set_high_pass_filter(int64_t handle, bool enabled, bool apply_in_full_band);
int32_t apm_set_echo_canceller(int64_t handle, int32_t mode, int32_t stream_delay_ms);
    // mode: 0=off, 1=mobile, 2=full; stream_delay_ms: -1=auto-detect (full mode only)
int32_t apm_set_noise_suppression(int64_t handle, bool enabled, int32_t level, bool analyze_linear_aec_output);
    // level: 0=low, 1=moderate, 2=high, 3=very_high
int32_t apm_set_pre_amplifier(int64_t handle, bool enabled, float fixed_gain_factor);
int32_t apm_set_capture_level_adjustment(int64_t handle, bool enabled, float pre_gain, float post_gain, bool mic_emu_enabled, uint8_t mic_emu_initial);
int32_t apm_set_gain_controller1(int64_t handle, bool enabled, int32_t mode, uint8_t target_level_dbfs, uint8_t compression_gain_db, bool enable_limiter);
    // mode: 0=adaptive_analog, 1=adaptive_digital, 2=fixed_digital
int32_t apm_set_analog_gain_controller(int64_t handle, bool enabled, int32_t startup_min_vol, int32_t clipped_level_min, bool enable_digital_adaptive, int32_t clipped_level_step, float clipped_ratio_threshold, int32_t clipped_wait_frames);
int32_t apm_set_clipping_predictor(int64_t handle, bool enabled, int32_t mode, int32_t window_length, int32_t ref_window_length, int32_t ref_window_delay, float clipping_threshold, float crest_factor_margin, bool use_predicted_step);
int32_t apm_set_gain_controller2(int64_t handle, bool enabled, bool input_vol_controller, float fixed_digital_gain_db);
int32_t apm_set_adaptive_digital(int64_t handle, bool enabled, float headroom_db, float max_gain_db, float initial_gain_db, float max_gain_change_db_per_sec, float max_output_noise_level_dbfs);
```

Each setter updates its section of an internal `Config` struct and calls `processor.set_config()`.

**Stats & hints:**

```c
int32_t apm_processor_get_stats(int64_t handle,
    int8_t* out_voice_detected,       // -1=unavailable, 0=false, 1=true
    double* out_erl,                  // NaN=unavailable
    double* out_erle,
    double* out_divergent_filter_fraction,
    int32_t* out_delay_median_ms,     // -1=unavailable
    int32_t* out_delay_std_ms,
    double* out_residual_echo_likelihood,
    double* out_residual_echo_likelihood_recent_max,
    int32_t* out_delay_ms);

void apm_processor_set_output_will_be_muted(int64_t handle, bool muted);
void apm_processor_set_stream_key_pressed(int64_t handle, bool pressed);
uint32_t apm_processor_num_samples_per_frame(int64_t handle);
```

**Handle management:** Handles are `Box::into_raw()` pointers cast to `i64`. A global `HashSet<i64>` validates handles before use to prevent use-after-free.

**Error codes:** Return `i32` where 0=success, negative=error. Error codes map 1:1 to the Rust `Error` enum.

## Java API (`com.manhhavu.webrtc.audio`)

### Processor

```java
public class Processor implements AutoCloseable {
    public Processor(int sampleRateHz) throws ProcessorException

    public void setConfig(Config config)
    public void reinitialize()

    // Mono (single channel)
    public void processCaptureFrame(float[] data)
    public void processRenderFrame(float[] data)
    public void analyzeRenderFrame(float[] data)

    // Multi-channel: float[channel][samples]
    public void processCaptureFrame(float[][] data)
    public void processRenderFrame(float[][] data)
    public void analyzeRenderFrame(float[][] data)

    public void setOutputWillBeMuted(boolean muted)
    public void setStreamKeyPressed(boolean pressed)

    public Stats getStats()
    public int numSamplesPerFrame()

    @Override
    public void close()
}
```

`setConfig()` dispatches to the per-group FFI calls based on which fields are non-null. Frame size must be `sampleRateHz / 100` samples per channel (10ms).

### Config Types

All config types are Java records. Nullable fields represent optional/disabled features.

```java
public record Config(
    Pipeline pipeline,
    CaptureAmplifier captureAmplifier,   // null = disabled
    HighPassFilter highPassFilter,        // null = disabled
    EchoCanceller echoCanceller,          // null = disabled
    NoiseSuppression noiseSuppression,    // null = disabled
    GainController gainController         // null = disabled
) {
    public static Config defaults() { return new Config(Pipeline.defaults(), null, null, null, null, null); }
}

public record Pipeline(
    PipelineProcessingRate maximumInternalProcessingRate,
    boolean multiChannelRender,
    boolean multiChannelCapture,
    DownmixMethod captureDownmixMethod
) {
    public static Pipeline defaults() {
        return new Pipeline(PipelineProcessingRate.MAX_48000_HZ, false, false, DownmixMethod.AVERAGE);
    }
}

public enum PipelineProcessingRate { MAX_32000_HZ, MAX_48000_HZ }
public enum DownmixMethod { AVERAGE, USE_FIRST_CHANNEL }

// Echo cancellation
public sealed interface EchoCanceller {
    record Mobile(int streamDelayMs) implements EchoCanceller {}
    record Full(Integer streamDelayMs) implements EchoCanceller {} // null = auto-detect
}

// Noise suppression
public record NoiseSuppression(NoiseSuppressionLevel level, boolean analyzeLinearAecOutput) {}
public enum NoiseSuppressionLevel { LOW, MODERATE, HIGH, VERY_HIGH }

// High-pass filter
public record HighPassFilter(boolean applyInFullBand) {}

// Capture amplifier
public sealed interface CaptureAmplifier {
    record PreAmplifier(float fixedGainFactor) implements CaptureAmplifier {}
    record CaptureLevelAdjustment(float preGainFactor, float postGainFactor,
                                   AnalogMicGainEmulation analogMicGainEmulation) implements CaptureAmplifier {}
}
public record AnalogMicGainEmulation(int initialLevel) {} // [0..255]

// Gain control
public sealed interface GainController {
    record GainController1(GainControllerMode mode, int targetLevelDbfs, int compressionGainDb,
                           boolean enableLimiter, AnalogGainController analogGainController) implements GainController {}
    record GainController2(boolean inputVolumeControllerEnabled, AdaptiveDigital adaptiveDigital,
                           FixedDigital fixedDigital) implements GainController {}
}

public enum GainControllerMode { ADAPTIVE_ANALOG, ADAPTIVE_DIGITAL, FIXED_DIGITAL }

public record AnalogGainController(int startupMinVolume, int clippedLevelMin, boolean enableDigitalAdaptive,
                                    int clippedLevelStep, float clippedRatioThreshold, int clippedWaitFrames,
                                    ClippingPredictor clippingPredictor) {} // clippingPredictor null = disabled

public record ClippingPredictor(ClippingPredictorMode mode, int windowLength, int referenceWindowLength,
                                 int referenceWindowDelay, float clippingThreshold, float crestFactorMargin,
                                 boolean usePredictedStep) {}
public enum ClippingPredictorMode { CLIPPING_EVENT_PREDICTION, ADAPTIVE_STEP_CLIPPING_PEAK_PREDICTION, FIXED_STEP_CLIPPING_PEAK_PREDICTION }

public record AdaptiveDigital(float headroomDb, float maxGainDb, float initialGainDb,
                               float maxGainChangeDbPerSecond, float maxOutputNoiseLevelDbfs) {}
public record FixedDigital(float gainDb) {}
```

### Stats

```java
public record Stats(
    Boolean voiceDetected,
    Double echoReturnLoss,
    Double echoReturnLossEnhancement,
    Double divergentFilterFraction,
    Integer delayMedianMs,
    Integer delayStandardDeviationMs,
    Double residualEchoLikelihood,
    Double residualEchoLikelihoodRecentMax,
    Integer delayMs
) {}
```

All fields nullable — `null` means the value was not available from the processor.

### Error Handling

```java
public class ProcessorException extends RuntimeException {
    public ErrorCode getErrorCode();
}

public enum ErrorCode {
    UNSPECIFIED, INITIALIZATION_FAILED, UNSUPPORTED_COMPONENT, UNSUPPORTED_FUNCTION,
    NULL_POINTER, BAD_PARAMETER, BAD_SAMPLE_RATE, BAD_DATA_LENGTH,
    BAD_NUMBER_CHANNELS, FILE_ERROR, STREAM_PARAMETER_NOT_SET, NOT_ENABLED
}
```

### NativeLib (internal)

```java
// Package-private JNA interface
interface NativeLib extends Library {
    // All extern "C" functions mapped here
    // Loaded via NativeLibLoader which extracts from classpath
}
```

## Native Library Loading

1. Detect platform from `os.name` + `os.arch`
2. Look for `/native/{platform}/libapm.{dylib,so}` on classpath (from natives JAR)
3. Extract to temp directory, load via JNA `Native.load(path)`
4. Fallback: try `java.library.path` for user-supplied library
5. Fail with clear error message listing expected platform and tried paths

## GitHub Actions (`.github/workflows/build.yml`)

```yaml
trigger: push to main, pull requests

jobs:
  build:
    strategy:
      matrix:
        include:
          - os: macos-latest      # aarch64
            target: macos-aarch64
            lib: libapm.dylib
          - os: ubuntu-latest     # x86_64
            target: linux-x86_64
            lib: libapm.so
    steps:
      - checkout (with submodules)
      - install Rust toolchain
      - setup Java 21 (Temurin)
      - cargo build --release (in native/)
      - ./gradlew build
      - upload native artifact

  package:
    needs: build
    steps:
      - download all native artifacts
      - package classifier JARs
      - smoke test: load native lib, create/destroy processor
```

## Testing

**Unit tests (no native lib needed):**
- Config record construction
- Error code mapping

**Integration tests (require native lib):**
- `ProcessorTest` — lifecycle, process silence, get stats
- `EchoCancellerTest` — verify echo suppression with known signals
- `NoiseSuppressionTest` — verify noise reduction
- `MultiChannelTest` — stereo processing
- `ConfigTest` — apply every config variant

Integration tests gated by native lib availability (skip if not found).

## Dependencies

- `net.java.dev.jna:jna:5.14.0` (runtime, ~2MB)
- No other external dependencies

## Consumer Usage

```kotlin
// build.gradle.kts
val os = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch")
val classifier = when {
    "mac" in os && arch == "aarch64" -> "macos-aarch64"
    "linux" in os && arch == "amd64" -> "linux-x86_64"
    else -> error("Unsupported platform")
}
dependencies {
    implementation("com.manhhavu:webrtc-audio-processing:0.1.0")
    runtimeOnly("com.manhhavu:webrtc-audio-processing-natives:0.1.0:$classifier")
}
```

```java
try (var processor = new Processor(48_000)) {
    processor.setConfig(new Config(
        Pipeline.defaults(),
        null, null,
        new EchoCanceller.Full(null),  // AEC with auto delay
        new NoiseSuppression(NoiseSuppressionLevel.MODERATE, false),
        null
    ));

    float[] capture = new float[480]; // 10ms at 48kHz
    float[] render  = new float[480];

    processor.analyzeRenderFrame(render);
    processor.processCaptureFrame(capture);

    Stats stats = processor.getStats();
    if (Boolean.TRUE.equals(stats.voiceDetected())) { ... }
}
```
