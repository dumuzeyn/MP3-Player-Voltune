package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Deterministic multi-start k-means++ with bounded silhouette sampling. */
final class SoundKMeans {
    private static final int ITERATIONS = 48;
    private static final int RESTARTS = 5;
    private static final int SILHOUETTE_SAMPLES = 256;

    private SoundKMeans() {
    }

    static Fit best(List<double[]> vectors, int k) {
        if (vectors.isEmpty() || k <= 0 || k > vectors.size()) {
            return Fit.empty();
        }
        if (k == 1) {
            int[] assignments = new int[vectors.size()];
            ArrayList<double[]> centroids = new ArrayList<>();
            centroids.add(mean(vectors));
            return finish(vectors, assignments, centroids);
        }
        Fit best = null;
        for (int restart = 0; restart < RESTARTS; restart++) {
            Fit candidate = run(vectors, k, restart);
            if (best == null || candidate.sse < best.sse) {
                best = candidate;
            }
        }
        return best == null ? Fit.empty() : best;
    }

    private static Fit run(List<double[]> vectors, int k, int restart) {
        Random random = new Random(0x5f3759dfL + vectors.size() * 1009L
                + k * 9176L + restart * 104729L);
        ArrayList<double[]> centroids = initialize(vectors, k, random);
        int[] assignments = new int[vectors.size()];
        Arrays.fill(assignments, -1);
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            boolean changed = assign(vectors, centroids, assignments);
            ArrayList<double[]> next = recompute(vectors, assignments, k, centroids);
            double shift = centroidShift(centroids, next);
            centroids = next;
            if (!changed || shift < 1.0e-6d) {
                break;
            }
        }
        assign(vectors, centroids, assignments);
        return finish(vectors, assignments, centroids);
    }

    private static ArrayList<double[]> initialize(List<double[]> vectors, int k,
            Random random) {
        ArrayList<double[]> centroids = new ArrayList<>();
        centroids.add(vectors.get(random.nextInt(vectors.size())).clone());
        double[] distances = new double[vectors.size()];
        while (centroids.size() < k) {
            double total = 0.0d;
            for (int index = 0; index < vectors.size(); index++) {
                double distance = squaredDistanceToNearest(vectors.get(index), centroids);
                distances[index] = distance;
                total += distance;
            }
            if (total <= 1.0e-12d) {
                centroids.add(vectors.get(centroids.size() % vectors.size()).clone());
                continue;
            }
            double target = random.nextDouble() * total;
            int selected = distances.length - 1;
            double accumulated = 0.0d;
            for (int index = 0; index < distances.length; index++) {
                accumulated += distances[index];
                if (accumulated >= target) {
                    selected = index;
                    break;
                }
            }
            centroids.add(vectors.get(selected).clone());
        }
        return centroids;
    }

    private static boolean assign(List<double[]> vectors, List<double[]> centroids,
            int[] assignments) {
        boolean changed = false;
        for (int index = 0; index < vectors.size(); index++) {
            int nearest = nearest(vectors.get(index), centroids);
            if (assignments[index] != nearest) {
                assignments[index] = nearest;
                changed = true;
            }
        }
        return changed;
    }

    private static ArrayList<double[]> recompute(List<double[]> vectors, int[] assignments,
            int k, List<double[]> previous) {
        int dimensions = vectors.get(0).length;
        double[][] sums = new double[k][dimensions];
        int[] counts = new int[k];
        for (int index = 0; index < vectors.size(); index++) {
            int cluster = assignments[index];
            counts[cluster]++;
            for (int feature = 0; feature < dimensions; feature++) {
                sums[cluster][feature] += vectors.get(index)[feature];
            }
        }
        ArrayList<double[]> result = new ArrayList<>();
        for (int cluster = 0; cluster < k; cluster++) {
            if (counts[cluster] == 0) {
                result.add(farthest(vectors, previous).clone());
                continue;
            }
            for (int feature = 0; feature < dimensions; feature++) {
                sums[cluster][feature] /= counts[cluster];
            }
            result.add(sums[cluster]);
        }
        return result;
    }

    private static double[] farthest(List<double[]> vectors, List<double[]> centroids) {
        double[] result = vectors.get(0);
        double farthest = -1.0d;
        for (double[] vector : vectors) {
            double distance = squaredDistanceToNearest(vector, centroids);
            if (distance > farthest) {
                farthest = distance;
                result = vector;
            }
        }
        return result;
    }

    private static Fit finish(List<double[]> vectors, int[] assignments,
            ArrayList<double[]> centroids) {
        double sse = 0.0d;
        int[] counts = new int[centroids.size()];
        for (int index = 0; index < vectors.size(); index++) {
            int cluster = assignments[index];
            counts[cluster]++;
            sse += squaredDistance(vectors.get(index), centroids.get(cluster));
        }
        double silhouette = silhouette(vectors, assignments, centroids.size(), counts);
        return new Fit(assignments.clone(), centroids, counts, sse, silhouette);
    }

    private static double silhouette(List<double[]> vectors, int[] assignments, int k,
            int[] counts) {
        if (k <= 1 || vectors.size() < 3) {
            return 0.0d;
        }
        int samples = Math.min(SILHOUETTE_SAMPLES, vectors.size());
        double total = 0.0d;
        for (int sample = 0; sample < samples; sample++) {
            int index = sample * vectors.size() / samples;
            int own = assignments[index];
            if (counts[own] <= 1) {
                continue;
            }
            double[] sums = new double[k];
            for (int other = 0; other < vectors.size(); other++) {
                if (other == index) continue;
                sums[assignments[other]] += Math.sqrt(squaredDistance(
                        vectors.get(index), vectors.get(other)));
            }
            double inside = sums[own] / (counts[own] - 1);
            double nearest = Double.POSITIVE_INFINITY;
            for (int cluster = 0; cluster < k; cluster++) {
                if (cluster != own && counts[cluster] > 0) {
                    nearest = Math.min(nearest, sums[cluster] / counts[cluster]);
                }
            }
            if (Double.isFinite(nearest)) {
                total += (nearest - inside) / Math.max(1.0e-9d,
                        Math.max(nearest, inside));
            }
        }
        return total / samples;
    }

    private static int nearest(double[] vector, List<double[]> centroids) {
        int nearest = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index < centroids.size(); index++) {
            double distance = squaredDistance(vector, centroids.get(index));
            if (distance < best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private static double squaredDistanceToNearest(double[] vector,
            List<double[]> centroids) {
        return squaredDistance(vector, centroids.get(nearest(vector, centroids)));
    }

    private static double squaredDistance(double[] left, double[] right) {
        double result = 0.0d;
        int count = Math.min(left.length, right.length);
        for (int index = 0; index < count; index++) {
            double delta = left[index] - right[index];
            result += delta * delta;
        }
        return result / Math.max(1, count);
    }

    private static double centroidShift(List<double[]> left, List<double[]> right) {
        double result = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            result += squaredDistance(left.get(index), right.get(index));
        }
        return result;
    }

    private static double[] mean(List<double[]> vectors) {
        double[] result = new double[vectors.get(0).length];
        for (double[] vector : vectors) {
            for (int feature = 0; feature < result.length; feature++) {
                result[feature] += vector[feature];
            }
        }
        for (int feature = 0; feature < result.length; feature++) {
            result[feature] /= vectors.size();
        }
        return result;
    }

    static final class Fit {
        final int[] assignments;
        final ArrayList<double[]> centroids;
        final int[] counts;
        final double sse;
        final double silhouette;

        Fit(int[] assignments, ArrayList<double[]> centroids, int[] counts,
                double sse, double silhouette) {
            this.assignments = assignments;
            this.centroids = centroids;
            this.counts = counts;
            this.sse = sse;
            this.silhouette = silhouette;
        }

        static Fit empty() {
            return new Fit(new int[0], new ArrayList<>(), new int[0],
                    Double.POSITIVE_INFINITY, 0.0d);
        }
    }
}
