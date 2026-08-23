package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MediaItemMapperTest {
    @Test
    public void matchesCurrentStableTrackId() {
        Track track = new Track("track-current", "content://music/current", "Song",
                "Artist", "Album", "Genre", 120000, 10L, 20L, "fingerprint");

        assertTrue(MediaItemMapper.matchesMediaId(track, "track-current"));
        assertFalse(MediaItemMapper.matchesMediaId(track, "track-other"));
    }

    @Test
    public void acceptsLegacyUriHashDuringUpgrade() {
        Track track = new Track("track-new", "content://music/legacy", "Song",
                "Artist", "Album", "Genre", 120000, 10L, 20L, "fingerprint");

        assertTrue(MediaItemMapper.matchesMediaId(track,
                TrackIdentity.fromLegacyUri(track.uri)));
    }
}
