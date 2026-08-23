package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class R128LoudnessMeterTest {
    @Test
    public void quietAndLoudSignalsHaveOrderedIntegratedLoudness() {
        LoudnessAnalysisResult quiet = sineResult(0.03f, false);
        LoudnessAnalysisResult loud = sineResult(0.45f, false);

        assertNotNull(quiet);
        assertNotNull(loud);
        assertTrue(loud.integratedLufs > quiet.integratedLufs + 15.0f);
    }

    @Test
    public void transientAndNearClippingSignalsArePeakLimited() {
        LoudnessAnalysisResult transientResult = sineResult(0.04f, true);
        LoudnessAnalysisResult nearClipping = sineResult(0.98f, false);

        assertNotNull(transientResult);
        assertNotNull(nearClipping);
        assertTrue(transientResult.peakDbfs > -0.2f);
        assertTrue(LoudnessGainPolicy.gainDb(nearClipping.integratedLufs,
                nearClipping.peakDbfs, false) <= 0.0f);
    }

    private static LoudnessAnalysisResult sineResult(float amplitude, boolean transientPeak) {
        int rate = 48000;
        int frames = rate * 2;
        float[] samples = new float[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            float sample = amplitude * (float) Math.sin(2.0 * Math.PI * 1000.0 * frame / rate);
            samples[frame * 2] = sample;
            samples[(frame * 2) + 1] = sample;
        }
        if (transientPeak) {
            samples[rate] = 1.0f;
            samples[rate + 1] = 1.0f;
        }
        R128LoudnessMeter meter = new R128LoudnessMeter(rate, 2);
        meter.addSamples(samples);
        return meter.result();
    }
}
