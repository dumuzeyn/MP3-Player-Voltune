package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class TrackDuplicatePolicyTest {
    @Test
    public void matchesOnePhysicalFileExposedByDifferentProviders() {
        Track mediaStore = track("media", "content://media/audio/11", "", 4096L);
        Track document = track("document", "content://documents/song", "same-hash", 4096L);

        assertEquals(0, TrackDuplicatePolicy.duplicateIndex(
                new ArrayList<>(Arrays.asList(mediaStore)), document));
    }

    @Test
    public void leavesAmbiguousCopiesSeparate() {
        Track first = track("first", "content://documents/first", "same-hash", 4096L);
        Track second = track("second", "content://documents/second", "same-hash", 4096L);
        Track discovered = track("new", "content://media/audio/11", "same-hash", 4096L);

        assertEquals(-1, TrackDuplicatePolicy.duplicateIndex(
                new ArrayList<>(Arrays.asList(first, second)), discovered));
    }

    @Test
    public void refreshedLocationKeepsIdentityAndStatistics() {
        Track stable = new Track("stable", "content://old", "Song", "Artist", "Album",
                "Artist", "Rock", 2024, 1, 1, 120000, 4096L, 10L, "hash",
                7, 2, 50L, 60L, 70L);
        Track fresh = track("fresh", "content://new", "hash", 4096L);

        Track merged = TrackDuplicatePolicy.withStableIdentity(stable, fresh);

        assertEquals("stable", merged.trackId);
        assertEquals("content://new", merged.uri);
        assertEquals(7, merged.playCount);
        assertEquals(2, merged.skipCount);
    }

    private static Track track(String id, String uri, String fingerprint, long size) {
        return new Track(id, uri, "Song", "Artist", "Album", "Rock", 120000,
                size, 42L, fingerprint);
    }
}
