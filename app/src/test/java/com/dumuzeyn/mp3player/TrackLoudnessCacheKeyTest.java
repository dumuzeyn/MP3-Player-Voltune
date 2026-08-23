package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class TrackLoudnessCacheKeyTest {
    @Test
    public void changedFileAndAlgorithmInvalidateCacheKey() {
        Track original = track(1000L, 2000L);
        Track changedSize = track(1001L, 2000L);
        Track changedDate = track(1000L, 2001L);

        assertNotEquals(TrackLoudnessNormalizer.cacheKeyFor(original),
                TrackLoudnessNormalizer.cacheKeyFor(changedSize));
        assertNotEquals(TrackLoudnessNormalizer.cacheKeyFor(original),
                TrackLoudnessNormalizer.cacheKeyFor(changedDate));
        assertNotEquals(TrackLoudnessNormalizer.cacheKeyFor(original, 2),
                TrackLoudnessNormalizer.cacheKeyFor(original, 3));
    }

    private static Track track(long size, long modified) {
        return new Track("track-fixed", "content://audio/1", "Title", "Artist",
                "Album", "Genre", 1000, size, modified, "fingerprint");
    }
}
