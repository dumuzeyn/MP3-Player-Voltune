package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Estimates tempo per decoded range and combines ranges with octave correction. */
final class TempoEstimator {
    private static final double ENVELOPE_RATE = 50.0d;
    private static final double MIN_BPM = 55.0d;
    private static final double MAX_BPM = 200.0d;

    private TempoEstimator() {
    }

    static Estimate estimateSegment(List<Double> envelope) {
        if (envelope == null || envelope.size() < 100) {
            return Estimate.empty();
        }
        double[] onset = onsetEnvelope(envelope);
        double activity = activity(onset);
        if (activity < 1.0e-5d) {
            return Estimate.empty();
        }
        int minimumLag = (int) Math.floor(ENVELOPE_RATE * 60.0d / MAX_BPM);
        int maximumLag = Math.min(onset.length / 2,
                (int) Math.ceil(ENVELOPE_RATE * 60.0d / MIN_BPM));
        double[] correlations = new double[maximumLag + 1];
        int bestLag = minimumLag;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int lag = minimumLag; lag <= maximumLag; lag++) {
            correlations[lag] = normalizedCorrelation(onset, lag);
            double harmonic = lag * 2 <= maximumLag ? correlations[lag * 2] : 0.0d;
            double score = correlations[lag] + Math.max(0.0d, harmonic) * 0.12d;
            if (score > bestScore) {
                bestScore = score;
                bestLag = lag;
            }
        }
        bestLag = correctOctave(bestLag, correlations, minimumLag, maximumLag);
        double bpm = ENVELOPE_RATE * 60.0d / bestLag;
        double peak = Math.max(0.0d, correlations[bestLag]);
        double contrast = Math.max(0.0d, peak - neighboringBaseline(correlations,
                bestLag, minimumLag, maximumLag));
        double confidence = clamp((peak * 0.72d + contrast * 1.8d)
                * Math.min(1.0d, activity * 28.0d));
        return confidence < 0.08d ? Estimate.empty() : new Estimate(bpm, confidence);
    }

    static Estimate combine(List<Estimate> source) {
        ArrayList<Estimate> estimates = new ArrayList<>();
        if (source != null) {
            for (Estimate estimate : source) {
                if (estimate != null && estimate.bpm >= MIN_BPM
                        && estimate.bpm <= MAX_BPM && estimate.confidence > 0.0d) {
                    estimates.add(estimate);
                }
            }
        }
        if (estimates.isEmpty()) {
            return Estimate.empty();
        }
        double anchor = weightedMedian(canonical(estimates));
        ArrayList<Estimate> corrected = new ArrayList<>();
        for (Estimate estimate : estimates) {
            corrected.add(new Estimate(closestOctave(estimate.bpm, anchor),
                    estimate.confidence));
        }
        double bpm = weightedMedian(corrected);
        double confidenceSum = 0.0d;
        double deviation = 0.0d;
        for (Estimate estimate : corrected) {
            confidenceSum += estimate.confidence;
            double ratio = Math.log(estimate.bpm / bpm);
            deviation += ratio * ratio * estimate.confidence;
        }
        double consistency = Math.exp(-Math.sqrt(deviation
                / Math.max(1.0e-9d, confidenceSum)) * 7.0d);
        double coverage = Math.min(1.0d, corrected.size() / 2.0d);
        double confidence = clamp(confidenceSum / corrected.size()
                * consistency * coverage);
        return new Estimate(bpm, confidence);
    }

    private static double[] onsetEnvelope(List<Double> envelope) {
        double[] levels = new double[envelope.size()];
        for (int index = 0; index < levels.length; index++) {
            levels[index] = Math.max(0.0d, envelope.get(index));
        }
        double[] sorted = levels.clone();
        Arrays.sort(sorted);
        double floor = sorted[(int) ((sorted.length - 1) * 0.20d)];
        double[] onset = new double[levels.length];
        for (int index = 1; index < levels.length; index++) {
            double previous = Math.max(0.0d, levels[index - 1] - floor);
            double current = Math.max(0.0d, levels[index] - floor);
            onset[index] = Math.sqrt(Math.max(0.0d, current - previous));
        }
        return onset;
    }

    private static double activity(double[] onset) {
        double sum = 0.0d;
        for (double value : onset) {
            sum += value;
        }
        return sum / Math.max(1, onset.length);
    }

    private static double normalizedCorrelation(double[] values, int lag) {
        double cross = 0.0d;
        double left = 0.0d;
        double right = 0.0d;
        for (int index = lag; index < values.length; index++) {
            double current = values[index];
            double delayed = values[index - lag];
            cross += current * delayed;
            left += current * current;
            right += delayed * delayed;
        }
        return cross / Math.sqrt(Math.max(1.0e-12d, left * right));
    }

    private static int correctOctave(int lag, double[] correlations,
            int minimumLag, int maximumLag) {
        double bpm = ENVELOPE_RATE * 60.0d / lag;
        int slower = lag * 2;
        if (bpm > 145.0d && slower <= maximumLag
                && correlations[slower] >= correlations[lag] * 0.78d) {
            return slower;
        }
        int faster = Math.max(minimumLag, lag / 2);
        if (bpm < 68.0d && faster < lag
                && correlations[faster] >= correlations[lag] * 0.92d) {
            return faster;
        }
        return lag;
    }

    private static double neighboringBaseline(double[] correlations, int lag,
            int minimumLag, int maximumLag) {
        double sum = 0.0d;
        int count = 0;
        for (int offset = -4; offset <= 4; offset++) {
            int candidate = lag + offset;
            if (Math.abs(offset) <= 1 || candidate < minimumLag || candidate > maximumLag) {
                continue;
            }
            sum += Math.max(0.0d, correlations[candidate]);
            count++;
        }
        return count == 0 ? 0.0d : sum / count;
    }

    private static ArrayList<Estimate> canonical(List<Estimate> source) {
        ArrayList<Estimate> result = new ArrayList<>();
        for (Estimate estimate : source) {
            double bpm = estimate.bpm;
            while (bpm > 150.0d) {
                bpm /= 2.0d;
            }
            while (bpm < 70.0d) {
                bpm *= 2.0d;
            }
            result.add(new Estimate(bpm, estimate.confidence));
        }
        return result;
    }

    private static double closestOctave(double bpm, double anchor) {
        double best = bpm;
        double bestDistance = Math.abs(Math.log(bpm / anchor));
        for (double candidate : new double[]{bpm / 2.0d, bpm * 2.0d}) {
            if (candidate < MIN_BPM || candidate > MAX_BPM) {
                continue;
            }
            double distance = Math.abs(Math.log(candidate / anchor));
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double weightedMedian(List<Estimate> values) {
        ArrayList<Estimate> ordered = new ArrayList<>(values);
        Collections.sort(ordered, Comparator.comparingDouble(value -> value.bpm));
        double total = 0.0d;
        for (Estimate value : ordered) {
            total += value.confidence;
        }
        double accumulated = 0.0d;
        for (Estimate value : ordered) {
            accumulated += value.confidence;
            if (accumulated >= total * 0.5d) {
                return value.bpm;
            }
        }
        return ordered.get(ordered.size() - 1).bpm;
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    static final class Estimate {
        final double bpm;
        final double confidence;

        Estimate(double bpm, double confidence) {
            this.bpm = bpm;
            this.confidence = confidence;
        }

        static Estimate empty() {
            return new Estimate(0.0d, 0.0d);
        }
    }
}
