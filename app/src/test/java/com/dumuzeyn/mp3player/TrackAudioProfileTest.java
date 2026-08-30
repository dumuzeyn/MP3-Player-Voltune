package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrackAudioProfileTest {
    @Test
    public void cacheMatchesOnlySameFileAndAnalysisVersion() {
        Track track = track(100L, 200L, "same");
        TrackAudioProfile profile = new TrackAudioProfile(track.trackId,
                TrackAudioProfile.ANALYSIS_VERSION, 100L, 200L, "same",
                SoundAnalysisState.ANALYZED,
                new double[TrackAudioProfile.FEATURE_COUNT], "", "", 0L);

        assertTrue(profile.matches(track));
        assertFalse(profile.matches(track(101L, 200L, "same")));
        assertFalse(profile.matches(track(100L, 201L, "same")));
        assertFalse(profile.matches(track(100L, 200L, "changed")));
        assertFalse(new TrackAudioProfile(track.trackId,
                TrackAudioProfile.ANALYSIS_VERSION - 1, 100L, 200L, "same",
                SoundAnalysisState.ANALYZED,
                new double[TrackAudioProfile.FEATURE_COUNT], "", "", 0L).matches(track));
    }

    @Test
    public void malformedAndNonFiniteVectorsCannotPoisonClustering() {
        assertFalse(new TrackAudioProfile("id", TrackAudioProfile.ANALYSIS_VERSION,
                1L, 1L, "", SoundAnalysisState.ANALYZED,
                new double[]{Double.NaN}, "", "", 0L).usable());
        assertFalse(TrackAudioProfile.decodeFeatures("broken,data").length > 0);
    }

    private static Track track(long size, long modified, String fingerprint) {
        return new Track("track", "content://track", "Title", "Artist", "Album",
                "Genre", 1000, size, modified, fingerprint);
    }
}
