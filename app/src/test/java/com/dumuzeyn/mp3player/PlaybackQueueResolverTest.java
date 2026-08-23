package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import org.junit.Test;

public class PlaybackQueueResolverTest {
    @Test
    public void restorePreservesSavedShuffleOrderAndSkipsMissingTracks() {
        ArrayList<Track> library = new ArrayList<>();
        library.add(new Track("uri:a", "A", "Artist"));
        library.add(new Track("uri:b", "B", "Artist"));
        library.add(new Track("uri:c", "C", "Artist"));

        ArrayList<Track> restored = PlaybackQueueResolver.restore(
                library,
                "[\"uri:c\",\"uri:missing\",\"uri:a\",\"uri:b\"]",
                library.get(0));

        assertEquals(3, restored.size());
        assertEquals("uri:c", restored.get(0).uri);
        assertEquals("uri:a", restored.get(1).uri);
        assertEquals("uri:b", restored.get(2).uri);
    }

    @Test
    public void restoreUsesCurrentTrackWhenSavedQueueIsDamaged() {
        ArrayList<Track> library = new ArrayList<>();
        Track current = new Track("uri:current", "Current", "Artist");
        library.add(current);

        ArrayList<Track> restored = PlaybackQueueResolver.restore(library, "not-json", current);

        assertEquals(1, restored.size());
        assertEquals("uri:current", restored.get(0).uri);
    }

    @Test
    public void restoreUsesDecodedUrisFromPlaybackState() {
        ArrayList<Track> library = new ArrayList<>();
        Track first = new Track("uri:first", "First", "Artist");
        Track second = new Track("uri:second", "Second", "Artist");
        library.add(first);
        library.add(second);
        ArrayList<String> savedUris = new ArrayList<>();
        savedUris.add("uri:second");
        savedUris.add("uri:first");

        ArrayList<Track> restored = PlaybackQueueResolver.restore(library, savedUris, first);

        assertEquals(2, restored.size());
        assertEquals("uri:second", restored.get(0).uri);
        assertEquals("uri:first", restored.get(1).uri);
    }

    @Test
    public void stableIdRestoresQueueAfterUriChanges() {
        ArrayList<Track> library = new ArrayList<>();
        Track moved = new Track("track-stable", "content://new/location", "Moved",
                "Artist", "Album", "Genre", 1000, 100L, 10L, "hash");
        library.add(moved);
        ArrayList<String> savedIds = new ArrayList<>();
        savedIds.add("track-stable");

        ArrayList<Track> restored = PlaybackQueueResolver.restore(library, savedIds, null);

        assertEquals(1, restored.size());
        assertEquals("content://new/location", restored.get(0).uri);
    }
}
