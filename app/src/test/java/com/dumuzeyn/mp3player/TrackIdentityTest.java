package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class TrackIdentityTest {
    @Test
    public void legacyIdentityIsStableAndDoesNotRevealUri() {
        String uri = "content://private/music/song.mp3";
        String first = TrackIdentity.fromLegacyUri(uri);
        assertEquals(first, TrackIdentity.fromLegacyUri(uri));
        assertFalse(first.contains(uri));
    }

    @Test
    public void mediaItemUsesTrackIdInsteadOfLocation() {
        Track track = new Track("track-permanent", "content://old/location", "Song",
                "Artist", "Album", "Genre", 1000, 10L, 20L, "fingerprint");
        assertEquals("track-permanent", new MediaItemMapper().mediaId(track));
    }
}
