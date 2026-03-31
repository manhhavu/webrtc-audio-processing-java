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
