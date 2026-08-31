package com.dumuzeyn.mp3player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Order-independent adaptive clustering for locally extracted audio profiles. */
final class SoundClusterEngine {
    static final int CLUSTERING_VERSION = 2;
    private static final int MIN_LIBRARY_SIZE = 4;
    private static final int MAX_CLUSTERS = 14;

    ArrayList<SoundGroup> cluster(List<TrackAudioProfile> source) {
        ArrayList<TrackAudioProfile> profiles = usableProfiles(source);
        if (profiles.size() < MIN_LIBRARY_SIZE) {
            return new ArrayList<>();
        }
        SoundFeatureNormalizer.Result normalized = SoundFeatureNormalizer.normalize(profiles);
        SoundKMeans.Fit fit = selectFit(normalized.vectors);
        ArrayList<Bucket> buckets = buckets(fit, profiles.size());
        splitHeterogeneousOversized(buckets, normalized.vectors, profiles.size());
        trimOutliers(buckets, normalized.vectors);

        ArrayList<SoundGroup> groups = new ArrayList<>();
        for (Bucket bucket : buckets) {
            if (bucket.members.size() < 2 && buckets.size() > 1) {
                continue;
            }
            ArrayList<TrackAudioProfile> members = new ArrayList<>();
            ArrayList<String> trackIds = new ArrayList<>();
            for (int index : bucket.members) {
                members.add(profiles.get(index));
                trackIds.add(profiles.get(index).trackId);
            }
            Collections.sort(trackIds);
            groups.add(new SoundGroup(stableId(trackIds), "", "",
                    SoundFeatureNormalizer.medianFeatures(members), trackIds));
        }
        Collections.sort(groups, (left, right) -> left.id.compareTo(right.id));
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
        double[] vector = normalization.vector(rawFeatures);
        Map<String, double[]> vectorsByTrack = new HashMap<>();
        for (int index = 0; index < usable.size(); index++) {
            vectorsByTrack.put(usable.get(index).trackId, normalization.vectors.get(index));
        }
        SoundGroup nearest = null;
        double best = Double.POSITIVE_INFINITY;
        double acceptance = 0.0d;
        for (SoundGroup group : groups) {
            double[] centroid = normalization.vector(group.centroid);
            double distance = SoundFeatureNormalizer.distance(vector, centroid);
            if (distance < best) {
                best = distance;
                nearest = group;
                acceptance = acceptanceDistance(group, centroid, vectorsByTrack);
            }
        }
        return nearest != null && best <= acceptance ? nearest.id : "";
    }

    private static SoundKMeans.Fit selectFit(List<double[]> vectors) {
        int maximum = Math.min(MAX_CLUSTERS, Math.max(2,
                (int) Math.round(Math.sqrt(vectors.size() / 2.0d))));
        maximum = Math.min(maximum, Math.max(1, vectors.size() / 2));
        SoundKMeans.Fit selected = SoundKMeans.best(vectors, 1);
        double selectedScore = 0.12d;
        for (int k = 2; k <= maximum; k++) {
            SoundKMeans.Fit candidate = SoundKMeans.best(vectors, k);
            double score = candidate.silhouette - 0.035d * (k - 1)
                    - imbalancePenalty(candidate.counts, vectors.size(),
                            candidate.silhouette);
            if (score > selectedScore + 0.012d) {
                selected = candidate;
                selectedScore = score;
            }
        }
        return selected;
    }

    private static double imbalancePenalty(int[] counts, int total, double silhouette) {
        int maximum = 0;
        for (int count : counts) maximum = Math.max(maximum, count);
        double share = maximum / (double) Math.max(1, total);
        return share > 0.72d && silhouette < 0.42d ? (share - 0.72d) * 0.8d : 0.0d;
    }

    private static ArrayList<Bucket> buckets(SoundKMeans.Fit fit, int count) {
        ArrayList<Bucket> result = new ArrayList<>();
        for (int cluster = 0; cluster < fit.centroids.size(); cluster++) {
            result.add(new Bucket());
        }
        for (int index = 0; index < count; index++) {
            result.get(fit.assignments[index]).members.add(index);
        }
        for (int index = result.size() - 1; index >= 0; index--) {
            if (result.get(index).members.isEmpty()) result.remove(index);
        }
        return result;
    }

    private static void splitHeterogeneousOversized(ArrayList<Bucket> buckets,
            List<double[]> vectors, int librarySize) {
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;
            int threshold = Math.max(12, (int) Math.ceil(librarySize * 0.38d));
            for (int index = 0; index < buckets.size(); index++) {
                Bucket bucket = buckets.get(index);
                if (bucket.members.size() < threshold) continue;
                ArrayList<double[]> subset = new ArrayList<>();
                for (int member : bucket.members) subset.add(vectors.get(member));
                SoundKMeans.Fit whole = SoundKMeans.best(subset, 1);
                SoundKMeans.Fit split = SoundKMeans.best(subset, 2);
                double improvement = 1.0d - split.sse / Math.max(1.0e-12d, whole.sse);
                if (improvement < 0.42d || split.silhouette < 0.34d
                        || split.counts[0] < 2 || split.counts[1] < 2) {
                    continue;
                }
                Bucket first = new Bucket();
                Bucket second = new Bucket();
                for (int member = 0; member < bucket.members.size(); member++) {
                    (split.assignments[member] == 0 ? first : second).members
                            .add(bucket.members.get(member));
                }
                buckets.set(index, first);
                buckets.add(index + 1, second);
                changed = true;
                break;
            }
            if (!changed) break;
        }
    }

    private static void trimOutliers(ArrayList<Bucket> buckets, List<double[]> vectors) {
        for (Bucket bucket : buckets) {
            if (bucket.members.size() < 6) continue;
            double[] centroid = mean(bucket.members, vectors);
            double[] distances = new double[bucket.members.size()];
            for (int index = 0; index < distances.length; index++) {
                distances[index] = SoundFeatureNormalizer.distance(
                        vectors.get(bucket.members.get(index)), centroid);
            }
            double[] sorted = distances.clone();
            Arrays.sort(sorted);
            double q1 = quantile(sorted, 0.25d);
            double q3 = quantile(sorted, 0.75d);
            double threshold = Math.max(0.82d, q3 + 2.5d * Math.max(0.05d, q3 - q1));
            ArrayList<Integer> retained = new ArrayList<>();
            for (int index = 0; index < distances.length; index++) {
                if (distances[index] <= threshold) retained.add(bucket.members.get(index));
            }
            if (retained.size() >= 2 && retained.size() >= bucket.members.size() * 0.80d) {
                bucket.members.clear();
                bucket.members.addAll(retained);
            }
        }
    }

    private static double acceptanceDistance(SoundGroup group, double[] centroid,
            Map<String, double[]> vectorsByTrack) {
        ArrayList<Double> values = new ArrayList<>();
        for (String trackId : group.trackIds) {
            double[] vector = vectorsByTrack.get(trackId);
            if (vector != null) {
                values.add(SoundFeatureNormalizer.distance(vector, centroid));
            }
        }
        if (values.size() < 3) return 1.15d;
        Collections.sort(values);
        double q1 = values.get((int) ((values.size() - 1) * 0.25d));
        double q3 = values.get((int) ((values.size() - 1) * 0.75d));
        return Math.max(0.86d, Math.min(2.4d, q3 + 2.5d * Math.max(0.05d, q3 - q1)));
    }

    private static double[] mean(List<Integer> members, List<double[]> vectors) {
        double[] result = new double[vectors.get(0).length];
        for (int member : members) {
            double[] vector = vectors.get(member);
            for (int feature = 0; feature < result.length; feature++) {
                result[feature] += vector[feature];
            }
        }
        for (int feature = 0; feature < result.length; feature++) {
            result[feature] /= members.size();
        }
        return result;
    }

    private static double quantile(double[] sorted, double fraction) {
        if (sorted.length == 0) return 0.0d;
        double position = fraction * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
    }

    private static ArrayList<TrackAudioProfile> usableProfiles(List<TrackAudioProfile> source) {
        ArrayList<TrackAudioProfile> result = new ArrayList<>();
        if (source != null) {
            for (TrackAudioProfile profile : source) {
                if (profile != null && profile.usable()) result.add(profile);
            }
        }
        Collections.sort(result, (left, right) -> left.trackId.compareTo(right.trackId));
        return result;
    }

    private static String stableId(List<String> trackIds) {
        StringBuilder joinedBuilder = new StringBuilder();
        for (String trackId : trackIds) {
            if (joinedBuilder.length() > 0) joinedBuilder.append('\n');
            joinedBuilder.append(trackId);
        }
        String joined = joinedBuilder.toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("similar-");
            for (int index = 0; index < 6; index++) {
                id.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            return id.toString();
        } catch (Exception ignored) {
            return "similar-" + Integer.toHexString(joined.hashCode());
        }
    }

    private static final class Bucket {
        final ArrayList<Integer> members = new ArrayList<>();
    }
}
