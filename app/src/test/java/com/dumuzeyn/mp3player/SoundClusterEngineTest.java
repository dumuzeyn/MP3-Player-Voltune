package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import org.junit.Test;

public class SoundClusterEngineTest {
    @Test
    public void emptyAndSmallLibrariesStayUngrouped() {
        SoundClusterEngine engine = new SoundClusterEngine();
        assertTrue(engine.cluster(new ArrayList<>()).isEmpty());
        assertTrue(engine.cluster(synthetic(3, 1)).isEmpty());
    }

    @Test
    public void clustersAreAdaptiveDeterministicAndNamedRelatively() {
        ArrayList<TrackAudioProfile> profiles = syntheticClouds(90, 3);
        SoundClusterEngine engine = new SoundClusterEngine();
        ArrayList<SoundGroup> first = engine.cluster(profiles);
        ArrayList<SoundGroup> second = engine.cluster(profiles);

        assertTrue(first.size() >= 2);
        assertTrue(first.size() <= Math.ceil(Math.sqrt(profiles.size())));
        assertEquals(ids(first), ids(second));
        HashSet<String> names = new HashSet<>();
        int assigned = 0;
        for (SoundGroup group : first) {
            assertEquals(2, group.nameRussian.split(" ").length);
            assertEquals(2, group.nameEnglish.split(" ").length);
            assertTrue(names.add(group.nameRussian));
            assigned += group.trackIds.size();
        }
        assertEquals(profiles.size(), assigned);
    }

    @Test
    public void incrementalTrackUsesNearestSavedCentroid() {
        ArrayList<TrackAudioProfile> profiles = syntheticClouds(80, 2);
        SoundClusterEngine engine = new SoundClusterEngine();
        ArrayList<SoundGroup> groups = engine.cluster(profiles);
        assertFalse(groups.isEmpty());
        String assigned = engine.nearestGroup(profiles.get(0).features, profiles, groups);
        assertFalse(assigned.isEmpty());
    }

    @Test(timeout = 12000L)
    public void syntheticLibrariesScaleToFiveThousandTracks() {
        SoundClusterEngine engine = new SoundClusterEngine();
        for (int size : new int[]{100, 500, 2000, 5000}) {
            long started = System.nanoTime();
            ArrayList<SoundGroup> groups = engine.cluster(syntheticClouds(size,
                    Math.max(2, (int) Math.sqrt(size) / 3)));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            System.out.println("sound_cluster_profile size=" + size + " elapsedMs="
                    + elapsedMs + " groups=" + groups.size());
            assertFalse("size=" + size, groups.isEmpty());
            assertTrue("size=" + size, groups.size() <= Math.ceil(Math.sqrt(size)));
            assertTrue("size=" + size + " elapsedMs=" + elapsedMs, elapsedMs < 5000L);
        }
    }

    private static ArrayList<String> ids(ArrayList<SoundGroup> groups) {
        ArrayList<String> result = new ArrayList<>();
        for (SoundGroup group : groups) {
            result.add(group.id + ":" + group.nameRussian);
        }
        return result;
    }

    private static ArrayList<TrackAudioProfile> synthetic(int count, int seed) {
        return syntheticClouds(count, Math.max(1, seed));
    }

    private static ArrayList<TrackAudioProfile> syntheticClouds(int count, int clouds) {
        Random random = new Random(8843L + count + clouds);
        ArrayList<TrackAudioProfile> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int cloud = index % clouds;
            double[] features = new double[TrackAudioProfile.FEATURE_COUNT];
            for (int feature = 0; feature < features.length; feature++) {
                features[feature] = cloud * 5.0d + feature * 0.1d
                        + random.nextGaussian() * 0.18d;
            }
            result.add(new TrackAudioProfile(String.format("track-%05d", index),
                    TrackAudioProfile.ANALYSIS_VERSION, index, index, "fp-" + index,
                    SoundAnalysisState.ANALYZED, features, "", "", 0L));
        }
        return result;
    }
}
