package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class SoundFeatureNormalizerTest {
    @Test
    public void everyFeatureIsCenteredAndScaled() {
        SoundFeatureNormalizer.Result result = SoundFeatureNormalizer.normalize(Arrays.asList(
                profile("a", filled(10.0d)), profile("b", filled(20.0d)),
                profile("c", filled(30.0d))));

        for (int feature = 0; feature < TrackAudioProfile.FEATURE_COUNT; feature++) {
            double mean = 0.0d;
            for (double[] vector : result.vectors) {
                mean += vector[feature];
            }
            assertEquals(0.0d, mean / result.vectors.size(), 1.0e-9d);
            assertEquals(20.0d, result.means[feature], 1.0e-9d);
        }
    }

    @Test
    public void distanceIsFiniteAndSymmetric() {
        double leftToRight = SoundFeatureNormalizer.distance(
                new double[]{0.0d, 1.0d}, new double[]{2.0d, 1.0d});
        double rightToLeft = SoundFeatureNormalizer.distance(
                new double[]{2.0d, 1.0d}, new double[]{0.0d, 1.0d});
        assertTrue(Double.isFinite(leftToRight));
        assertEquals(leftToRight, rightToLeft, 0.0d);
    }

    private static TrackAudioProfile profile(String id, double[] features) {
        return new TrackAudioProfile(id, TrackAudioProfile.ANALYSIS_VERSION, 1L, 2L, "fp",
                SoundAnalysisState.ANALYZED, features, "", "", 0L);
    }

    private static double[] filled(double value) {
        double[] result = new double[TrackAudioProfile.FEATURE_COUNT];
        Arrays.fill(result, value);
        return result;
    }
}
