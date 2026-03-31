package com.manhhavu.webrtc.audio;

public enum DownmixMethod {
    AVERAGE(0),
    USE_FIRST_CHANNEL(1);

    private final int value;

    DownmixMethod(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
