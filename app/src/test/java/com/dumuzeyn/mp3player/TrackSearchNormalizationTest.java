package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrackSearchNormalizationTest {
    @Test
    public void trackBuildsNormalizedSearchTextOnceFromMetadata() {
        Track track = new Track(
                "content://music/1",
                "  LUA   NA PRACA ",
                "ArtIST",
                "Night Album",
                "Funk",
                1000);

        assertEquals(
                "lua na praca artist night album funk",
                track.normalizedSearchText);
        assertTrue(track.normalizedSearchText.contains(
                Track.normalizeSearchText("  LUA na  ")));
    }

    @Test
    public void nullAndWhitespaceQueriesNormalizeSafely() {
        assertEquals("", Track.normalizeSearchText(null));
        assertEquals("", Track.normalizeSearchText("   "));
        assertEquals("one two", Track.normalizeSearchText("One\t\nTwo"));
    }
}
