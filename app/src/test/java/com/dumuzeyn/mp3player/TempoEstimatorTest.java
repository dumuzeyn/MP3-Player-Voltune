package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class TempoEstimatorTest {
    @Test
    public void combinesSegmentsAndRejectsDoubleTimeOutlier() {
        TempoEstimator.Estimate combined = TempoEstimator.combine(Arrays.asList(
                new TempoEstimator.Estimate(78.0d, 0.86d),
                new TempoEstimator.Estimate(79.0d, 0.82d),
                new TempoEstimator.Estimate(156.0d, 0.55d)));

        assertTrue("bpm=" + combined.bpm, combined.bpm >= 77.0d && combined.bpm <= 80.0d);
        assertTrue("confidence=" + combined.confidence, combined.confidence > 0.45d);
    }

    @Test
    public void estimatesSlowAndFastPulsesWithoutOctaveSwap() {
        TempoEstimator.Estimate slow = TempoEstimator.estimateSegment(pulses(78.0d, 12));
        TempoEstimator.Estimate fast = TempoEstimator.estimateSegment(pulses(132.0d, 12));

        assertTrue("slow=" + slow.bpm, slow.bpm >= 74.0d && slow.bpm <= 82.0d);
        assertTrue("fast=" + fast.bpm, fast.bpm >= 126.0d && fast.bpm <= 138.0d);
        assertTrue(slow.confidence > 0.20d);
        assertTrue(fast.confidence > 0.20d);
    }

    @Test
    public void silenceHasNoConfidentTempo() {
        ArrayList<Double> silence = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            silence.add(0.0d);
        }
        TempoEstimator.Estimate estimate = TempoEstimator.estimateSegment(silence);
        assertEquals(0.0d, estimate.bpm, 0.0d);
        assertEquals(0.0d, estimate.confidence, 0.0d);
    }

    private static ArrayList<Double> pulses(double bpm, int seconds) {
        int count = seconds * 50;
        double period = 3000.0d / bpm;
        ArrayList<Double> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double phase = index % period;
            double pulse = phase < 2.0d ? 0.85d : 0.03d;
            values.add(pulse);
        }
        return values;
    }
}
