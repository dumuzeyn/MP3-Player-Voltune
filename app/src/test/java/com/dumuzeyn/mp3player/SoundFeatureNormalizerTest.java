package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class SoundFeatureNormalizerTest {
    @Test
    public void includedFeaturesUsePreKMeansStandardScaling() {
        SoundFeatureNormalizer.Result result = SoundFeatureNormalizer.normalize(Arrays.asList(
                profile("a", filled(10.0d)), profile("b", filled(20.0d)),
                profile("c", filled(30.0d))));

        for (int feature = 0; feature < TrackAudioProfile.FEATURE_COUNT; feature++) {
            if (feature == TrackAudioProfile.BPM
                    || feature == TrackAudioProfile.TEMPO_CONFIDENCE) continue;
            double mean = 0.0d;
            for (double[] vector : result.vectors) mean += vector[feature];
            assertEquals(0.0d, mean / result.vectors.size(), 1.0e-9d);
            assertEquals(20.0d, result.means[feature], 1.0e-9d);
        }
    }

    @Test
    public void bpmAndTempoConfidenceAreAbsentFromVectorsAndDistance() {
        double[] first = filled(1.0d);
        double[] second = filled(1.0d);
        first[TrackAudioProfile.BPM] = 55.0d;
        second[TrackAudioProfile.BPM] = 190.0d;
        first[TrackAudioProfile.TEMPO_CONFIDENCE] = 0.0d;
        second[TrackAudioProfile.TEMPO_CONFIDENCE] = 1.0d;
        SoundFeatureNormalizer.Result result = SoundFeatureNormalizer.normalize(Arrays.asList(
                profile("a", first), profile("b", second)));

        assertEquals(0.0d, result.vectors.get(0)[TrackAudioProfile.BPM], 0.0d);
        assertEquals(0.0d, result.vectors.get(1)[TrackAudioProfile.TEMPO_CONFIDENCE], 0.0d);
        assertEquals(0.0d, SoundFeatureNormalizer.distance(
                result.vectors.get(0), result.vectors.get(1)), 0.0d);
    }

    @Test
    public void distanceIsFiniteAndSymmetric() {
        double[] left = new double[TrackAudioProfile.FEATURE_COUNT];
        double[] right = new double[TrackAudioProfile.FEATURE_COUNT];
        right[TrackAudioProfile.ENERGY] = 2.0d;
        double leftToRight = SoundFeatureNormalizer.distance(left, right);
        double rightToLeft = SoundFeatureNormalizer.distance(right, left);
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
