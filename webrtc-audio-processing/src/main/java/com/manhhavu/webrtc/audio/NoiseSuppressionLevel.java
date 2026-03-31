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
