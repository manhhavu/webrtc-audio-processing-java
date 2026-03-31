package com.manhhavu.webrtc.audio;

public class Processor implements AutoCloseable {
    private final NativeLib lib;
    private final long handle;
    private volatile boolean closed;

    public Processor(int sampleRateHz) {
        this.lib = NativeLibLoader.load();
        this.handle = lib.apm_processor_new(sampleRateHz);
        ProcessorException.checkHandle(handle);
        this.closed = false;
    }

    public void setConfig(Config config) {
        ensureOpen();
        applyPipeline(config.pipeline());
        applyHighPassFilter(config.highPassFilter());
        applyEchoCanceller(config.echoCanceller());
        applyNoiseSuppression(config.noiseSuppression());
        applyCaptureAmplifier(config.captureAmplifier());
        applyGainController(config.gainController());
    }

    public void reinitialize() {
        ensureOpen();
        lib.apm_processor_reinitialize(handle);
    }

    public void processCaptureFrame(float[] data) {
        ensureOpen();
        ProcessorException.checkResult(
                lib.apm_processor_process_capture(handle, data, 1, data.length));
    }

    public void processCaptureFrame(float[][] data) {
        ensureOpen();
        float[] flat = flatten(data);
        ProcessorException.checkResult(
                lib.apm_processor_process_capture(handle, flat, data.length, data[0].length));
        unflatten(flat, data);
    }

    public void processRenderFrame(float[] data) {
        ensureOpen();
        ProcessorException.checkResult(
                lib.apm_processor_process_render(handle, data, 1, data.length));
    }

    public void processRenderFrame(float[][] data) {
        ensureOpen();
        float[] flat = flatten(data);
        ProcessorException.checkResult(
                lib.apm_processor_process_render(handle, flat, data.length, data[0].length));
        unflatten(flat, data);
    }

    public void analyzeRenderFrame(float[] data) {
        ensureOpen();
        ProcessorException.checkResult(
                lib.apm_processor_analyze_render(handle, data, 1, data.length));
    }

    public void analyzeRenderFrame(float[][] data) {
        ensureOpen();
        float[] flat = flatten(data);
        ProcessorException.checkResult(
                lib.apm_processor_analyze_render(handle, flat, data.length, data[0].length));
    }

    public void setOutputWillBeMuted(boolean muted) {
        ensureOpen();
        lib.apm_processor_set_output_will_be_muted(handle, muted);
    }

    public void setStreamKeyPressed(boolean pressed) {
        ensureOpen();
        lib.apm_processor_set_stream_key_pressed(handle, pressed);
    }

    public Stats getStats() {
        ensureOpen();
        byte[] voiceDetected = new byte[1];
        double[] erl = new double[1];
        double[] erle = new double[1];
        double[] divergent = new double[1];
        int[] delayMedian = new int[1];
        int[] delayStd = new int[1];
        double[] residual = new double[1];
        double[] residualMax = new double[1];
        int[] delay = new int[1];

        ProcessorException.checkResult(lib.apm_processor_get_stats(
                handle, voiceDetected, erl, erle, divergent,
                delayMedian, delayStd, residual, residualMax, delay));

        return new Stats(
                voiceDetected[0] == -1 ? null : voiceDetected[0] == 1,
                Double.isNaN(erl[0]) ? null : erl[0],
                Double.isNaN(erle[0]) ? null : erle[0],
                Double.isNaN(divergent[0]) ? null : divergent[0],
                delayMedian[0] == -1 ? null : delayMedian[0],
                delayStd[0] == -1 ? null : delayStd[0],
                Double.isNaN(residual[0]) ? null : residual[0],
                Double.isNaN(residualMax[0]) ? null : residualMax[0],
                delay[0] == -1 ? null : delay[0]);
    }

    public int numSamplesPerFrame() {
        ensureOpen();
        return lib.apm_processor_num_samples_per_frame(handle);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            lib.apm_processor_destroy(handle);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Processor is closed");
        }
    }

    private void applyPipeline(Pipeline p) {
        if (p == null) return;
        ProcessorException.checkResult(lib.apm_set_pipeline(
                handle,
                p.maximumInternalProcessingRate().value(),
                p.multiChannelRender(),
                p.multiChannelCapture(),
                p.captureDownmixMethod().value()));
    }

    private void applyHighPassFilter(HighPassFilter hpf) {
        ProcessorException.checkResult(lib.apm_set_high_pass_filter(
                handle, hpf != null, hpf != null && hpf.applyInFullBand()));
    }

    private void applyEchoCanceller(EchoCanceller ec) {
        if (ec == null) {
            ProcessorException.checkResult(lib.apm_set_echo_canceller(handle, 0, 0));
        } else if (ec instanceof EchoCanceller.Mobile m) {
            ProcessorException.checkResult(lib.apm_set_echo_canceller(handle, 1, m.streamDelayMs()));
        } else if (ec instanceof EchoCanceller.Full f) {
            ProcessorException.checkResult(lib.apm_set_echo_canceller(
                    handle, 2, f.streamDelayMs() != null ? f.streamDelayMs() : -1));
        }
    }

    private void applyNoiseSuppression(NoiseSuppression ns) {
        ProcessorException.checkResult(lib.apm_set_noise_suppression(
                handle, ns != null,
                ns != null ? ns.level().value() : 1,
                ns != null && ns.analyzeLinearAecOutput()));
    }

    private void applyCaptureAmplifier(CaptureAmplifier ca) {
        if (ca == null) {
            ProcessorException.checkResult(lib.apm_set_pre_amplifier(handle, false, 1.0f));
        } else if (ca instanceof CaptureAmplifier.PreAmplifier pa) {
            ProcessorException.checkResult(lib.apm_set_pre_amplifier(handle, true, pa.fixedGainFactor()));
        } else if (ca instanceof CaptureAmplifier.CaptureLevelAdjustment cla) {
            var emu = cla.analogMicGainEmulation();
            ProcessorException.checkResult(lib.apm_set_capture_level_adjustment(
                    handle, true, cla.preGainFactor(), cla.postGainFactor(),
                    emu != null, emu != null ? (byte) emu.initialLevel() : (byte) -1));
        }
    }

    private void applyGainController(GainController gc) {
        if (gc == null) {
            ProcessorException.checkResult(lib.apm_set_gain_controller1(
                    handle, false, 2, (byte) 3, (byte) 9, true));
            return;
        }
        if (gc instanceof GainController.GainController1 gc1) {
            ProcessorException.checkResult(lib.apm_set_gain_controller1(
                    handle, true, gc1.mode().value(),
                    (byte) gc1.targetLevelDbfs(), (byte) gc1.compressionGainDb(),
                    gc1.enableLimiter()));
            var agc = gc1.analogGainController();
            if (agc != null) {
                ProcessorException.checkResult(lib.apm_set_analog_gain_controller(
                        handle, true, agc.startupMinVolume(), agc.clippedLevelMin(),
                        agc.enableDigitalAdaptive(), agc.clippedLevelStep(),
                        agc.clippedRatioThreshold(), agc.clippedWaitFrames()));
                var cp = agc.clippingPredictor();
                if (cp != null) {
                    ProcessorException.checkResult(lib.apm_set_clipping_predictor(
                            handle, true, cp.mode().value(), cp.windowLength(),
                            cp.referenceWindowLength(), cp.referenceWindowDelay(),
                            cp.clippingThreshold(), cp.crestFactorMargin(),
                            cp.usePredictedStep()));
                }
            }
        } else if (gc instanceof GainController.GainController2 gc2) {
            ProcessorException.checkResult(lib.apm_set_gain_controller2(
                    handle, true, gc2.inputVolumeControllerEnabled(),
                    gc2.fixedDigital() != null ? gc2.fixedDigital().gainDb() : 0.0f));
            var ad = gc2.adaptiveDigital();
            if (ad != null) {
                ProcessorException.checkResult(lib.apm_set_adaptive_digital(
                        handle, true, ad.headroomDb(), ad.maxGainDb(), ad.initialGainDb(),
                        ad.maxGainChangeDbPerSecond(), ad.maxOutputNoiseLevelDbfs()));
            }
        }
    }

    private static float[] flatten(float[][] channels) {
        int numChannels = channels.length;
        int samplesPerChannel = channels[0].length;
        float[] flat = new float[numChannels * samplesPerChannel];
        for (int ch = 0; ch < numChannels; ch++) {
            System.arraycopy(channels[ch], 0, flat, ch * samplesPerChannel, samplesPerChannel);
        }
        return flat;
    }

    private static void unflatten(float[] flat, float[][] channels) {
        int samplesPerChannel = channels[0].length;
        for (int ch = 0; ch < channels.length; ch++) {
            System.arraycopy(flat, ch * samplesPerChannel, channels[ch], 0, samplesPerChannel);
        }
    }
}
