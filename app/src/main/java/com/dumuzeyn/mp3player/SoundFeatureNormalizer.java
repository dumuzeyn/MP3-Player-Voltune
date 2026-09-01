package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;

/** Pre-KMeans standard scaling with tempo dimensions explicitly excluded. */
final class SoundFeatureNormalizer {
    private static final double EPSILON = 1.0e-9d;

    private SoundFeatureNormalizer() {
    }

    static Result normalize(List<TrackAudioProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return new Result(new double[0], new double[0], new ArrayList<>());
        }
        int dimensions = TrackAudioProfile.FEATURE_COUNT;
        double[] means = new double[dimensions];
        for (TrackAudioProfile profile : profiles) {
            for (int feature = 0; feature < dimensions; feature++) {
                if (included(feature)) means[feature] += profile.features[feature];
            }
        }
        for (int feature = 0; feature < dimensions; feature++) {
            if (included(feature)) means[feature] /= profiles.size();
        }
        double[] deviations = new double[dimensions];
        for (TrackAudioProfile profile : profiles) {
            for (int feature = 0; feature < dimensions; feature++) {
                if (!included(feature)) continue;
                double delta = profile.features[feature] - means[feature];
                deviations[feature] += delta * delta;
            }
        }
        for (int feature = 0; feature < dimensions; feature++) {
            if (!included(feature)) {
                deviations[feature] = 1.0d;
                continue;
            }
            deviations[feature] = Math.sqrt(deviations[feature] / profiles.size());
            if (deviations[feature] < EPSILON) deviations[feature] = 1.0d;
        }
        ArrayList<double[]> vectors = new ArrayList<>();
        for (TrackAudioProfile profile : profiles) {
            vectors.add(vector(profile.features, means, deviations));
        }
        return new Result(means, deviations, vectors);
    }

    static double distance(double[] left, double[] right) {
        int count = Math.min(left.length, right.length);
        double sum = 0.0d;
        int includedCount = 0;
        for (int index = 0; index < count; index++) {
            if (!included(index)) continue;
            double delta = left[index] - right[index];
            sum += delta * delta;
            includedCount++;
        }
        return includedCount == 0 ? Double.POSITIVE_INFINITY
                : Math.sqrt(sum / includedCount);
    }

    private static double[] vector(double[] raw, double[] means, double[] deviations) {
        double[] result = new double[TrackAudioProfile.FEATURE_COUNT];
        for (int feature = 0; feature < result.length; feature++) {
            if (!included(feature)) continue;
            double value = feature < raw.length ? raw[feature] : 0.0d;
            result[feature] = (value - means[feature]) / deviations[feature];
        }
        return result;
    }

    private static boolean included(int feature) {
        return feature != TrackAudioProfile.BPM
                && feature != TrackAudioProfile.TEMPO_CONFIDENCE;
    }

    static final class Result {
        final double[] means;
        final double[] deviations;
        final ArrayList<double[]> vectors;

        Result(double[] means, double[] deviations, ArrayList<double[]> vectors) {
            this.means = means;
            this.deviations = deviations;
            this.vectors = vectors;
        }

        double[] vector(double[] raw) {
            return SoundFeatureNormalizer.vector(raw, means, deviations);
        }
    }
}
