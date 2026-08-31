package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class SoundGroupNamerTest {
    @Test
    public void slowTempoNeverBecomesFastBecauseAnotherGroupIsSlower() {
        SoundGroup slow = group("slow", 82.0d, 0.92d, 0.13d, 0.20d, 0.28d);
        SoundGroup slower = group("slower", 62.0d, 0.88d, 0.12d, 0.21d, 0.27d);

        ArrayList<SoundGroup> named = SoundGroupNamer.name(Arrays.asList(slow, slower));

        assertTrue(find(named, "slow").nameRussian.contains("Медленный темп"));
        assertFalse(find(named, "slow").nameRussian.contains("Быстрый"));
    }

    @Test
    public void uncertainTempoIsNotUsedForName() {
        SoundGroup uncertain = group("uncertain", 172.0d, 0.18d,
                0.28d, 0.20d, 0.55d);

        String name = SoundGroupNamer.name(Arrays.asList(uncertain)).get(0).nameRussian;

        assertFalse(name.contains("Быстрый темп"));
        assertTrue(name.contains("Высокая энергия") || name.contains("Яркий спектр"));
    }

    @Test
    public void duplicateCharacteristicsKeepTruthfulNameWithoutInventedTrait() {
        SoundGroup first = group("first", 80.0d, 0.90d, 0.06d, 0.20d, 0.25d);
        SoundGroup second = group("second", 81.0d, 0.91d, 0.06d, 0.20d, 0.25d);

        ArrayList<SoundGroup> named = SoundGroupNamer.name(Arrays.asList(first, second));

        assertEquals(find(named, "first").nameRussian, find(named, "second").nameRussian);
        assertFalse(find(named, "second").nameRussian.contains("Быстрый"));
    }

    private static SoundGroup group(String id, double bpm, double confidence,
            double energy, double bass, double centroid) {
        double[] values = new double[TrackAudioProfile.FEATURE_COUNT];
        values[TrackAudioProfile.BPM] = bpm;
        values[TrackAudioProfile.TEMPO_CONFIDENCE] = confidence;
        values[TrackAudioProfile.ENERGY] = energy;
        values[TrackAudioProfile.BASS] = bass;
        values[TrackAudioProfile.CENTROID] = centroid;
        values[TrackAudioProfile.DYNAMIC_RANGE] = 9.0d;
        values[TrackAudioProfile.RHYTHM] = 0.10d;
        return new SoundGroup(id, "", "", values, new ArrayList<>());
    }

    private static SoundGroup find(ArrayList<SoundGroup> groups, String id) {
        for (SoundGroup group : groups) {
            if (id.equals(group.id)) return group;
        }
        throw new AssertionError("Missing group " + id);
    }
}
