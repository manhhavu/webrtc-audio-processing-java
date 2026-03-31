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
