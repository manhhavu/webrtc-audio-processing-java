package com.manhhavu.webrtc.audio;

public enum PipelineProcessingRate {
    MAX_32000_HZ(32000),
    MAX_48000_HZ(48000);

    private final int value;

    PipelineProcessingRate(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
