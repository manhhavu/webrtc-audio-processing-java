package com.manhhavu.webrtc.audio;

public sealed interface EchoCanceller {
    record Mobile(int streamDelayMs) implements EchoCanceller {}
    record Full(Integer streamDelayMs) implements EchoCanceller {}
}
