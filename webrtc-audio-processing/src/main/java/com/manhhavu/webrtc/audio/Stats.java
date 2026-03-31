package com.manhhavu.webrtc.audio;

public record Stats(
        Boolean voiceDetected,
        Double echoReturnLoss,
        Double echoReturnLossEnhancement,
        Double divergentFilterFraction,
        Integer delayMedianMs,
        Integer delayStandardDeviationMs,
        Double residualEchoLikelihood,
        Double residualEchoLikelihoodRecentMax,
        Integer delayMs) {}
