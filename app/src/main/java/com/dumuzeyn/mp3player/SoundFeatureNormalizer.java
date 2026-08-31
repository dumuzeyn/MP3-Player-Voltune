package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Robustly scales audio features and balances related feature families. */
final class SoundFeatureNormalizer {
    private static final double EPSILON = 1.0e-9d;
    private static final double CLIP = 4.0d;

    private SoundFeatureNormalizer() {
    }

    static Result normalize(List<TrackAudioProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return new Result(new double[0], new double[0], new ArrayList<>());
        }
        int dimensions = TrackAudioProfile.FEATURE_COUNT;
        double[] centers = new double[dimensions];
        double[] scales = new double[dimensions];
        for (int feature = 0; feature < dimensions; feature++) {
            double[] values = featureValues(profiles, feature);
            Arrays.sort(values);
            centers[feature] = quantile(values, 0.50d);
            double iqr = quantile(values, 0.75d) - quantile(values, 0.25d);
            double scale = iqr / 1.349d;
            if (scale < EPSILON) {
                double[] deviations = new double[values.length];
                for (int index = 0; index < values.length; index++) {
                    deviations[index] = Math.abs(values[index] - centers[feature]);
                }
                Arrays.sort(deviations);
                scale = quantile(deviations, 0.50d) * 1.4826d;
            }
            scales[feature] = scale < EPSILON ? 1.0d : scale;
        }
        ArrayList<double[]> vectors = new ArrayList<>();
        for (TrackAudioProfile profile : profiles) {
            vectors.add(vector(profile.features, centers, scales));
        }
        return new Result(centers, scales, vectors);
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

    static double[] medianFeatures(List<TrackAudioProfile> profiles) {
        double[] result = new double[TrackAudioProfile.FEATURE_COUNT];
        if (profiles == null || profiles.isEmpty()) {
            return result;
        }
        for (int feature = 0; feature < result.length; feature++) {
            double[] values = featureValues(profiles, feature);
            Arrays.sort(values);
            result[feature] = quantile(values, 0.50d);
        }
        return result;
    }

    private static double[] vector(double[] raw, double[] centers, double[] scales) {
        double[] result = new double[TrackAudioProfile.FEATURE_COUNT];
        double confidence = raw.length > TrackAudioProfile.TEMPO_CONFIDENCE
                ? clamp(raw[TrackAudioProfile.TEMPO_CONFIDENCE], 0.0d, 1.0d) : 0.0d;
        for (int feature = 0; feature < result.length; feature++) {
            double value = feature < raw.length ? raw[feature] : 0.0d;
            double normalized = clamp((value - centers[feature]) / scales[feature],
                    -CLIP, CLIP);
            double weight = weight(feature);
            if (feature == TrackAudioProfile.BPM) {
                weight *= Math.sqrt(confidence);
            }
            result[feature] = normalized * weight;
        }
        return result;
    }

    private static double weight(int feature) {
        if (feature == TrackAudioProfile.BPM) return 0.72d;
        if (feature == TrackAudioProfile.ENERGY) return 0.90d;
        if (feature == TrackAudioProfile.LOUDNESS) return 0.48d;
        if (feature == TrackAudioProfile.DYNAMIC_RANGE) return 0.68d;
        if (feature == TrackAudioProfile.CENTROID) return 0.68d;
        if (feature == TrackAudioProfile.BANDWIDTH) return 0.42d;
        if (feature == TrackAudioProfile.ROLLOFF) return 0.42d;
        if (feature == TrackAudioProfile.ZERO_CROSSING) return 0.34d;
        if (feature == TrackAudioProfile.BASS) return 0.78d;
        if (feature == TrackAudioProfile.TREBLE) return 0.62d;
        if (feature == TrackAudioProfile.RHYTHM) return 0.70d;
        if (feature == TrackAudioProfile.CONTRAST) return 0.46d;
        if (feature == TrackAudioProfile.TEMPO_CONFIDENCE) return 0.0d;
        return 0.28d;
    }

    private static double[] featureValues(List<TrackAudioProfile> profiles, int feature) {
        double[] result = new double[profiles.size()];
        for (int index = 0; index < profiles.size(); index++) {
            double[] values = profiles.get(index).features;
            result[index] = feature < values.length ? values[feature] : 0.0d;
        }
        return result;
    }

    private static double quantile(double[] sorted, double fraction) {
        if (sorted.length == 0) {
            return 0.0d;
        }
        double position = clamp(fraction, 0.0d, 1.0d) * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        double mix = position - lower;
        return sorted[lower] * (1.0d - mix) + sorted[upper] * mix;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Result {
        final double[] centers;
        final double[] scales;
        final ArrayList<double[]> vectors;

        Result(double[] centers, double[] scales, ArrayList<double[]> vectors) {
            this.centers = centers;
            this.scales = scales;
            this.vectors = vectors;
        }

        double[] vector(double[] raw) {
            return SoundFeatureNormalizer.vector(raw, centers, scales);
        }
    }
}
