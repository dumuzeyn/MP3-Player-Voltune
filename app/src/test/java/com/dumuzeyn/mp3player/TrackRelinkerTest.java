package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TrackRelinkerTest {
    @Test
    public void fingerprintSizeAndMetadataMatchMovedTrack() {
        Track old = track("track-old", "content://old", "hash-a", 1000L,
                "Song", "Artist");
        Track unrelated = track("track-other", "content://other", "hash-b", 2000L,
                "Other", "Artist");
        Track moved = track("track-new", "content://new", "hash-a", 1000L,
                "Song", "Artist");

        List<Track> matches = TrackRelinker.candidates(Arrays.asList(old, unrelated), moved);

        assertEquals(1, matches.size());
        assertEquals("track-old", matches.get(0).trackId);
    }

    @Test
    public void weakNameOnlyMatchIsRejected() {
        Track old = track("track-old", "content://old", "", -1L,
                "Song", "Artist");
        Track discovered = track("track-new", "content://new", "", -1L,
                "Song", "Artist");
        assertTrue(TrackRelinker.candidates(Arrays.asList(old), discovered).isEmpty());
    }

    private static Track track(String id, String uri, String fingerprint, long size,
            String title, String artist) {
        return new Track(id, uri, title, artist, "Album", "Genre", 120000,
                size, 123L, fingerprint);
    }
}
