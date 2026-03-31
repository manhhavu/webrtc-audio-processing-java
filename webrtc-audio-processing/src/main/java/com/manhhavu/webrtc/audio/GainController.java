package com.manhhavu.webrtc.audio;

public sealed interface GainController {
    record GainController1(
            GainControllerMode mode,
            int targetLevelDbfs,
            int compressionGainDb,
            boolean enableLimiter,
            AnalogGainController analogGainController) implements GainController {}

    record GainController2(
            boolean inputVolumeControllerEnabled,
            AdaptiveDigital adaptiveDigital,
            FixedDigital fixedDigital) implements GainController {}
}
