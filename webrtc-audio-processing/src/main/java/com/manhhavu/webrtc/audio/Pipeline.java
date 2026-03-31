package com.manhhavu.webrtc.audio;

public record Pipeline(
        PipelineProcessingRate maximumInternalProcessingRate,
        boolean multiChannelRender,
        boolean multiChannelCapture,
        DownmixMethod captureDownmixMethod) {

    public static Pipeline defaults() {
        return new Pipeline(PipelineProcessingRate.MAX_48000_HZ, false, false, DownmixMethod.AVERAGE);
    }
}
