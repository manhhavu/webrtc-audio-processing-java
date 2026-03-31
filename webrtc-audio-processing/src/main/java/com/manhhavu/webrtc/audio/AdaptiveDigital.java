package com.manhhavu.webrtc.audio;

public record AdaptiveDigital(
        float headroomDb,
        float maxGainDb,
        float initialGainDb,
        float maxGainChangeDbPerSecond,
        float maxOutputNoiseLevelDbfs) {}
