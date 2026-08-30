package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import org.junit.Test;

public class SoundGroupNamerTest {
    @Test
    public void namesDescribePositionRelativeToCurrentLibrary() {
        ArrayList<SoundGroup> groups = new ArrayList<>();
        groups.add(group("high", TrackAudioProfile.ENERGY, 3.0d));
        groups.add(group("low", TrackAudioProfile.ENERGY, -3.0d));

        ArrayList<SoundGroup> named = SoundGroupNamer.name(groups);

        assertEquals("Энергичный поток", find(named, "high").nameRussian);
        assertEquals("Спокойный поток", find(named, "low").nameRussian);
    }

    @Test
    public void identicalProfilesStillReceiveUniqueTwoWordNamesWhenPossible() {
        ArrayList<SoundGroup> groups = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            groups.add(group("same-" + index, TrackAudioProfile.ENERGY, 1.0d));
        }

        HashSet<String> russian = new HashSet<>();
        HashSet<String> english = new HashSet<>();
        for (SoundGroup group : SoundGroupNamer.name(groups)) {
            assertEquals(2, group.nameRussian.split(" ").length);
            assertEquals(2, group.nameEnglish.split(" ").length);
            assertTrue(russian.add(group.nameRussian));
            assertTrue(english.add(group.nameEnglish));
        }
    }

    private static SoundGroup group(String id, int feature, double value) {
        double[] centroid = new double[TrackAudioProfile.FEATURE_COUNT];
        centroid[feature] = value;
        return new SoundGroup(id, "", "", centroid, new ArrayList<>());
    }

    private static SoundGroup find(ArrayList<SoundGroup> groups, String id) {
        for (SoundGroup group : groups) {
            if (id.equals(group.id)) {
                return group;
            }
        }
        throw new AssertionError("Missing group " + id);
    }
}
