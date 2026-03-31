package com.manhhavu.webrtc.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APM_LIB_PATH", matches = ".+")
class ConfigTest {

    private Processor processor;

    @BeforeEach
    void setUp() {
        System.setProperty("jna.library.path", System.getenv("APM_LIB_PATH"));
        processor = new Processor(48_000);
    }

    @AfterEach
    void tearDown() {
        if (processor != null) {
            processor.close();
        }
    }

    @Test
    void defaultConfig() {
        processor.setConfig(Config.defaults());
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void echoCancellerMobile() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Mobile(10),
                null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void echoCancellerFull() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Full(null),
                null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void noiseSuppression() {
        for (NoiseSuppressionLevel level : NoiseSuppressionLevel.values()) {
            processor.setConfig(new Config(
                    Pipeline.defaults(), null, null, null,
                    new NoiseSuppression(level, false),
                    null));
            float[] frame = new float[480];
            processor.processCaptureFrame(frame);
        }
    }

    @Test
    void highPassFilter() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null,
                new HighPassFilter(true),
                null, null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void preAmplifier() {
        processor.setConfig(new Config(
                Pipeline.defaults(),
                new CaptureAmplifier.PreAmplifier(2.0f),
                null, null, null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void captureLevelAdjustment() {
        processor.setConfig(new Config(
                Pipeline.defaults(),
                new CaptureAmplifier.CaptureLevelAdjustment(1.5f, 1.2f,
                        new AnalogMicGainEmulation(200)),
                null, null, null, null));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void gainController1() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null, null, null,
                new GainController.GainController1(
                        GainControllerMode.FIXED_DIGITAL, 3, 9, true, null)));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void gainController2() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null, null, null,
                new GainController.GainController2(false,
                        new AdaptiveDigital(5.0f, 50.0f, 15.0f, 6.0f, -50.0f),
                        new FixedDigital(0.0f))));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void fullConfig() {
        processor.setConfig(new Config(
                new Pipeline(PipelineProcessingRate.MAX_48000_HZ, false, false, DownmixMethod.AVERAGE),
                new CaptureAmplifier.PreAmplifier(1.0f),
                new HighPassFilter(true),
                new EchoCanceller.Full(null),
                new NoiseSuppression(NoiseSuppressionLevel.HIGH, false),
                new GainController.GainController1(
                        GainControllerMode.ADAPTIVE_DIGITAL, 3, 9, true, null)));
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }
}
