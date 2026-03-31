package com.manhhavu.webrtc.audio;

public record ClippingPredictor(
        ClippingPredictorMode mode,
        int windowLength,
        int referenceWindowLength,
        int referenceWindowDelay,
        float clippingThreshold,
        float crestFactorMargin,
        boolean usePredictedStep) {}
