package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class SoundGroupNamerTest {
    @Test
    public void bpmCannotChangeAGroupName() {
        SoundGroup slow = group("same", 55.0d, 1.8d);
        SoundGroup fast = group("same", 195.0d, 1.8d);

        String slowName = SoundGroupNamer.name(Arrays.asList(slow)).get(0).nameRussian;
        String fastName = SoundGroupNamer.name(Arrays.asList(fast)).get(0).nameRussian;

        assertEquals(slowName, fastName);
    }

    @Test
    public void namesAreShortCollectionTitlesWithoutTechnicalSeparator() {
        SoundGroup group = group("energy", 120.0d, 2.2d);
        String name = SoundGroupNamer.name(Arrays.asList(group)).get(0).nameRussian;

        assertEquals("Энергичный поток", name);
        assertFalse(name.contains("·"));
        assertFalse(name.toLowerCase().contains("темп"));
    }

    @Test
    public void duplicateNamesReceiveDeterministicMusicalFallbacks() {
        ArrayList<SoundGroup> named = SoundGroupNamer.name(Arrays.asList(
                group("first", 80.0d, 2.0d), group("second", 160.0d, 2.0d)));

        assertFalse(named.get(0).nameRussian.isEmpty());
        assertFalse(named.get(1).nameRussian.isEmpty());
        assertFalse(named.get(0).nameRussian.contains("·"));
        assertFalse(named.get(1).nameRussian.contains("·"));
    }

    private static SoundGroup group(String id, double bpm, double energy) {
        double[] values = new double[TrackAudioProfile.FEATURE_COUNT];
        values[TrackAudioProfile.BPM] = bpm;
        values[TrackAudioProfile.TEMPO_CONFIDENCE] = 1.0d;
        values[TrackAudioProfile.ENERGY] = energy;
        return new SoundGroup(id, "", "", values, new ArrayList<>());
    }
}
