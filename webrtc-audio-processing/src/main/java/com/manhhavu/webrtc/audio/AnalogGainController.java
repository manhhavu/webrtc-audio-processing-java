package com.manhhavu.webrtc.audio;

public record AnalogGainController(
        int startupMinVolume,
        int clippedLevelMin,
        boolean enableDigitalAdaptive,
        int clippedLevelStep,
        float clippedRatioThreshold,
        int clippedWaitFrames,
        ClippingPredictor clippingPredictor) {}
