package com.manhhavu.webrtc.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APM_LIB_PATH", matches = ".+")
class ProcessorTest {

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
    void createAndDestroy() {
        assertNotNull(processor);
        assertEquals(480, processor.numSamplesPerFrame());
    }

    @Test
    void processSilence() {
        float[] capture = new float[480];
        float[] render = new float[480];

        processor.analyzeRenderFrame(render);
        processor.processCaptureFrame(capture);

        for (float sample : capture) {
            assertEquals(0.0f, sample, 1e-6f);
        }
    }

    @Test
    void getStatsAfterProcessing() {
        float[] frame = new float[480];
        processor.processCaptureFrame(frame);

        Stats stats = processor.getStats();
        assertNotNull(stats);
    }

    @Test
    void processWithEchoCanceller() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Full(null),
                null, null));

        float[] render = new float[480];
        float[] capture = new float[480];

        for (int i = 0; i < render.length; i++) {
            render[i] = (float) Math.sin(2 * Math.PI * 440 * i / 48000.0);
        }
        System.arraycopy(render, 0, capture, 0, render.length);

        processor.analyzeRenderFrame(render);
        processor.processCaptureFrame(capture);
    }

    @Test
    void processMultiChannel() {
        processor.setConfig(new Config(
                new Pipeline(PipelineProcessingRate.MAX_48000_HZ, true, true, DownmixMethod.AVERAGE),
                null, null, null, null, null));

        float[][] stereoCapture = new float[2][480];
        float[][] stereoRender = new float[2][480];

        processor.analyzeRenderFrame(stereoRender);
        processor.processCaptureFrame(stereoCapture);
    }

    @Test
    void reinitialize() {
        processor.setConfig(new Config(
                Pipeline.defaults(), null, null,
                new EchoCanceller.Full(null),
                null, null));

        processor.reinitialize();

        float[] frame = new float[480];
        processor.processCaptureFrame(frame);
    }

    @Test
    void closedProcessorThrows() {
        processor.close();
        assertThrows(IllegalStateException.class, () -> processor.processCaptureFrame(new float[480]));
    }

    @Test
    void setOutputWillBeMuted() {
        processor.setOutputWillBeMuted(true);
        processor.setOutputWillBeMuted(false);
    }

    @Test
    void setStreamKeyPressed() {
        processor.setStreamKeyPressed(true);
        processor.setStreamKeyPressed(false);
    }

    @Test
    void differentSampleRates() {
        try (var p8k = new Processor(8_000)) {
            assertEquals(80, p8k.numSamplesPerFrame());
            float[] frame = new float[80];
            p8k.processCaptureFrame(frame);
        }
        try (var p16k = new Processor(16_000)) {
            assertEquals(160, p16k.numSamplesPerFrame());
            float[] frame = new float[160];
            p16k.processCaptureFrame(frame);
        }
    }
}
