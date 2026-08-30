package com.dumuzeyn.mp3player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class SoundClusterEngine {
    private static final int MIN_LIBRARY_SIZE = 4;
    private static final int DISTANCE_SAMPLE_LIMIT = 256;

    ArrayList<SoundGroup> cluster(List<TrackAudioProfile> source) {
        ArrayList<TrackAudioProfile> profiles = usableProfiles(source);
        if (profiles.size() < MIN_LIBRARY_SIZE) {
            return new ArrayList<>();
        }
        SoundFeatureNormalizer.Result normalized = SoundFeatureNormalizer.normalize(profiles);
        double threshold = adaptiveThreshold(normalized.vectors);
        ArrayList<MutableCluster> clusters = new ArrayList<>();
        for (int index = 0; index < profiles.size(); index++) {
            addToNearest(clusters, profiles.get(index).trackId,
                    normalized.vectors.get(index), threshold);
        }
        mergeSmallClusters(clusters, Math.max(2,
                (int) Math.round(Math.sqrt(profiles.size()) / 4.0d)));
        int adaptiveMaximum = Math.max(2, (int) Math.ceil(Math.sqrt(profiles.size())));
        while (clusters.size() > adaptiveMaximum) {
            mergeClosestPair(clusters);
        }
        ArrayList<SoundGroup> groups = new ArrayList<>();
        for (MutableCluster cluster : clusters) {
            cluster.trackIds.sort(String::compareTo);
            groups.add(new SoundGroup(stableId(cluster.trackIds), "", "",
                    cluster.centroid, cluster.trackIds));
        }
        return SoundGroupNamer.name(groups);
    }

    String nearestGroup(double[] rawFeatures, List<TrackAudioProfile> library,
            List<SoundGroup> groups) {
        if (rawFeatures == null || rawFeatures.length != TrackAudioProfile.FEATURE_COUNT
                || groups == null || groups.isEmpty()) {
            return "";
        }
        ArrayList<TrackAudioProfile> usable = usableProfiles(library);
        if (usable.isEmpty()) {
            return "";
        }
        SoundFeatureNormalizer.Result normalization = SoundFeatureNormalizer.normalize(usable);
        double[] vector = new double[TrackAudioProfile.FEATURE_COUNT];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (rawFeatures[index] - normalization.means[index])
                    / normalization.deviations[index];
        }
        SoundGroup nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (SoundGroup group : groups) {
            double current = SoundFeatureNormalizer.distance(vector, group.centroid);
            if (current < distance) {
                distance = current;
                nearest = group;
            }
        }
        return nearest == null ? "" : nearest.id;
    }

    private static ArrayList<TrackAudioProfile> usableProfiles(List<TrackAudioProfile> source) {
        ArrayList<TrackAudioProfile> result = new ArrayList<>();
        if (source != null) {
            for (TrackAudioProfile profile : source) {
                if (profile != null && profile.usable()) {
                    result.add(profile);
                }
            }
        }
        result.sort(Comparator.comparing(profile -> profile.trackId));
        return result;
    }

    private static double adaptiveThreshold(List<double[]> vectors) {
        int count = Math.min(vectors.size(), DISTANCE_SAMPLE_LIMIT);
        ArrayList<Double> nearest = new ArrayList<>();
        for (int left = 0; left < count; left++) {
            double best = Double.POSITIVE_INFINITY;
            for (int right = 0; right < count; right++) {
                if (left != right) {
                    best = Math.min(best, SoundFeatureNormalizer.distance(
                            vectors.get(left), vectors.get(right)));
                }
            }
            if (Double.isFinite(best)) {
                nearest.add(best);
            }
        }
        nearest.sort(Double::compareTo);
        double median = nearest.isEmpty() ? 0.75d : nearest.get(nearest.size() / 2);
        return Math.max(0.35d, Math.min(1.75d, median * 1.35d));
    }

    private static void addToNearest(ArrayList<MutableCluster> clusters, String trackId,
            double[] vector, double threshold) {
        MutableCluster nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (MutableCluster cluster : clusters) {
            double current = SoundFeatureNormalizer.distance(vector, cluster.centroid);
            if (current < distance) {
                distance = current;
                nearest = cluster;
            }
        }
        if (nearest == null || distance > threshold) {
            clusters.add(new MutableCluster(trackId, vector));
        } else {
            nearest.add(trackId, vector);
        }
    }

    private static void mergeSmallClusters(ArrayList<MutableCluster> clusters, int minimum) {
        boolean changed = true;
        while (changed && clusters.size() > 1) {
            changed = false;
            for (int index = 0; index < clusters.size(); index++) {
                MutableCluster small = clusters.get(index);
                if (small.trackIds.size() >= minimum) {
                    continue;
                }
                int nearest = nearestClusterIndex(clusters, index);
                if (nearest >= 0) {
                    MutableCluster target = clusters.get(nearest);
                    target.merge(small);
                    clusters.remove(index);
                    changed = true;
                    break;
                }
            }
        }
    }

    private static void mergeClosestPair(ArrayList<MutableCluster> clusters) {
        int bestLeft = 0;
        int bestRight = 1;
        double best = Double.POSITIVE_INFINITY;
        for (int left = 0; left < clusters.size(); left++) {
            for (int right = left + 1; right < clusters.size(); right++) {
                double distance = SoundFeatureNormalizer.distance(
                        clusters.get(left).centroid, clusters.get(right).centroid);
                if (distance < best) {
                    best = distance;
                    bestLeft = left;
                    bestRight = right;
                }
            }
        }
        clusters.get(bestLeft).merge(clusters.remove(bestRight));
    }

    private static int nearestClusterIndex(ArrayList<MutableCluster> clusters, int source) {
        int nearest = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index < clusters.size(); index++) {
            if (index == source) {
                continue;
            }
            double distance = SoundFeatureNormalizer.distance(
                    clusters.get(source).centroid, clusters.get(index).centroid);
            if (distance < best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private static String stableId(List<String> trackIds) {
        String joined = String.join("\n", trackIds);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("sound-");
            for (int index = 0; index < 6; index++) {
                id.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            return id.toString();
        } catch (Exception ignored) {
            return "sound-" + Integer.toHexString(joined.hashCode());
        }
    }

    private static final class MutableCluster {
        final ArrayList<String> trackIds = new ArrayList<>();
        double[] centroid;

        MutableCluster(String trackId, double[] vector) {
            trackIds.add(trackId);
            centroid = vector.clone();
        }

        void add(String trackId, double[] vector) {
            int previous = trackIds.size();
            trackIds.add(trackId);
            for (int index = 0; index < centroid.length; index++) {
                centroid[index] = (centroid[index] * previous + vector[index])
                        / (previous + 1);
            }
        }

        void merge(MutableCluster other) {
            int total = trackIds.size() + other.trackIds.size();
            for (int index = 0; index < centroid.length; index++) {
                centroid[index] = (centroid[index] * trackIds.size()
                        + other.centroid[index] * other.trackIds.size()) / total;
            }
            trackIds.addAll(other.trackIds);
        }
    }
}
