package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class LibraryRepositoryTest {
    @Test
    public void findsTrackByUriAndStableId() {
        ArrayList<Track> tracks = new ArrayList<>();
        Track track = new Track("id-1", "content://song/1", "Song", "Artist",
                "Album", "Genre", 1_000, 10L, 20L, "fingerprint");
        tracks.add(track);
        LibraryRepository repository = repository(tracks, new HashSet<>(), new AtomicInteger());

        assertSame(track, repository.find(track.uri));
        assertSame(track, repository.find(track.trackId));
        assertNull(repository.find("missing"));
    }

    @Test
    public void favoriteMutationPersistsOnlyCollections() {
        ArrayList<Track> tracks = new ArrayList<>();
        Track track = new Track("content://song/1", "Song", "Artist");
        tracks.add(track);
        HashSet<String> favorites = new HashSet<>();
        AtomicInteger saves = new AtomicInteger();
        LibraryRepository repository = repository(tracks, favorites, saves);

        assertTrue(repository.toggleFavorite(track));
        assertTrue(favorites.contains(track.uri));
        assertFalse(repository.toggleFavorite(track));
        assertFalse(favorites.contains(track.uri));
        assertEquals(2, saves.get());
    }

    private LibraryRepository repository(ArrayList<Track> tracks,
            HashSet<String> favorites, AtomicInteger saves) {
        return new LibraryRepository(tracks, favorites, new ArrayList<>(),
                (savedFavorites, playlists) -> saves.incrementAndGet());
    }
}
