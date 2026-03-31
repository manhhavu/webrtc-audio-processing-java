# webrtc-audio-processing-java

Java 21+ wrapper for [webrtc-audio-processing](https://github.com/tonarino/webrtc-audio-processing) — Google's WebRTC audio processing module via JNA.

Provides echo cancellation (AEC3), noise suppression, automatic gain control, and voice activity detection as a Maven/Gradle dependency with bundled native libraries.

## Usage

```kotlin
// build.gradle.kts
val os = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch")
val classifier = when {
    "mac" in os && arch == "aarch64" -> "macos-aarch64"
    "linux" in os && arch == "amd64" -> "linux-x86_64"
    else -> error("Unsupported platform: $os $arch")
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
        new EchoCanceller.Full(null),    // AEC with auto delay detection
        new NoiseSuppression(NoiseSuppressionLevel.MODERATE, false),
        null
    ));

    float[] capture = new float[480];  // 10ms at 48kHz
    float[] render  = new float[480];

    processor.analyzeRenderFrame(render);
    processor.processCaptureFrame(capture);

    Stats stats = processor.getStats();
}
```

## Platforms

| Platform | Classifier |
|---|---|
| macOS aarch64 (Apple Silicon) | `macos-aarch64` |
| Linux x86_64 | `linux-x86_64` |

## API

The Java API mirrors the Rust crate's full API:

- **`Processor`** — create, configure, process audio frames, get stats
- **`Config`** — pipeline, echo canceller (mobile/full), noise suppression, gain control (AGC1/AGC2), high-pass filter, capture amplifier
- **`Stats`** — voice detection, echo return loss, delay estimates

All config types use Java records and sealed interfaces. Frame size is fixed at 10ms (`sampleRate / 100` samples).

## Building from source

Requires Java 21+, Rust, meson, and ninja. The Rust crate at `../webrtc-audio-processing` must be checked out.

```bash
cd native && cargo build --release && cd ..
./gradlew build -x test
APM_LIB_PATH=$(pwd)/native/target/release ./gradlew test
```

## License

BSD-3-Clause (same as the upstream WebRTC audio processing library).
