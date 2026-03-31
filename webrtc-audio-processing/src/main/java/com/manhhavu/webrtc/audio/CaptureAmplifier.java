package com.manhhavu.webrtc.audio;

public sealed interface CaptureAmplifier {
    record PreAmplifier(float fixedGainFactor) implements CaptureAmplifier {}
    record CaptureLevelAdjustment(
            float preGainFactor,
            float postGainFactor,
            AnalogMicGainEmulation analogMicGainEmulation) implements CaptureAmplifier {}
}
