package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;

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
                means[feature] += profile.features[feature];
            }
        }
        for (int feature = 0; feature < dimensions; feature++) {
            means[feature] /= profiles.size();
        }
        double[] deviations = new double[dimensions];
        for (TrackAudioProfile profile : profiles) {
            for (int feature = 0; feature < dimensions; feature++) {
                double delta = profile.features[feature] - means[feature];
                deviations[feature] += delta * delta;
            }
        }
        for (int feature = 0; feature < dimensions; feature++) {
            deviations[feature] = Math.sqrt(deviations[feature] / profiles.size());
            if (deviations[feature] < EPSILON) {
                deviations[feature] = 1.0d;
            }
        }
        ArrayList<double[]> vectors = new ArrayList<>();
        for (TrackAudioProfile profile : profiles) {
            double[] vector = new double[dimensions];
            for (int feature = 0; feature < dimensions; feature++) {
                vector[feature] = (profile.features[feature] - means[feature])
                        / deviations[feature];
            }
            vectors.add(vector);
        }
        return new Result(means, deviations, vectors);
    }

    static double distance(double[] left, double[] right) {
        int count = Math.min(left.length, right.length);
        if (count == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double sum = 0.0d;
        for (int index = 0; index < count; index++) {
            double delta = left[index] - right[index];
            sum += delta * delta;
        }
        return Math.sqrt(sum / count);
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
    }
}
