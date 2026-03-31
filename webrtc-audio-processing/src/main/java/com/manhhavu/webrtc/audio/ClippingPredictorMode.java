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
