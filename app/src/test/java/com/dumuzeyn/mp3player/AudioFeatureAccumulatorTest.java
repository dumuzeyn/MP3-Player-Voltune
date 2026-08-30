package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import org.junit.Test;

public class AudioFeatureAccumulatorTest {
    private static final int SAMPLE_RATE = 8000;

    @Test
    public void extractsDifferentSpectralProfilesFromLowAndHighTones() {
        double[] low = tone(120.0d, 3.0d);
        double[] high = tone(2800.0d, 3.0d);

        assertEquals(TrackAudioProfile.FEATURE_COUNT, low.length);
        assertTrue(high[TrackAudioProfile.CENTROID] > low[TrackAudioProfile.CENTROID]);
        assertTrue(low[TrackAudioProfile.BASS] > high[TrackAudioProfile.BASS]);
        assertTrue(high[TrackAudioProfile.ZERO_CROSSING]
                > low[TrackAudioProfile.ZERO_CROSSING]);
    }

    @Test
    public void detectsPulseTempoWithoutKeepingPcm() {
        AudioFeatureAccumulator accumulator = new AudioFeatureAccumulator(SAMPLE_RATE);
        int total = SAMPLE_RATE * 12;
        int beat = SAMPLE_RATE / 2;
        for (int index = 0; index < total; index++) {
            int insideBeat = index % beat;
            accumulator.addSample(insideBeat < SAMPLE_RATE / 30 ? 0.8f : 0.02f);
        }
        double bpm = accumulator.finish()[TrackAudioProfile.BPM];
        assertTrue("bpm=" + bpm, bpm >= 115.0d && bpm <= 125.0d);
    }

    @Test
    public void representativeRangesCoverStartMiddleAndEndWithoutOverlapForLongTrack() {
        ArrayList<Long> starts = AudioFeatureExtractor.representativeStarts(180_000_000L);
        assertEquals(3, starts.size());
        assertEquals(Long.valueOf(0L), starts.get(0));
        assertEquals(Long.valueOf(85_000_000L), starts.get(1));
        assertEquals(Long.valueOf(170_000_000L), starts.get(2));
    }

    private static double[] tone(double frequency, double seconds) {
        AudioFeatureAccumulator accumulator = new AudioFeatureAccumulator(SAMPLE_RATE);
        int samples = (int) (SAMPLE_RATE * seconds);
        for (int index = 0; index < samples; index++) {
            accumulator.addSample((float) (0.6d * Math.sin(
                    2.0d * Math.PI * frequency * index / SAMPLE_RATE)));
        }
        return accumulator.finish();
    }
}
