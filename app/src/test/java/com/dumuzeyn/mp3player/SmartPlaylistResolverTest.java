package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class SmartPlaylistResolverTest {
    @Test
    public void resolvesWithoutMutatingLibrary() {
        Track older = track("older", 3, 100L, 10L);
        Track recent = track("recent", 1, 300L, 20L);
        List<Track> source = Arrays.asList(older, recent);

        List<Track> result = new SmartPlaylistResolver().resolve(
                SmartPlaylistDefinition.RECENTLY_PLAYED, source, Collections.emptySet(),
                1_000L, 10);

        assertEquals("recent", result.get(0).title);
        assertEquals("older", source.get(0).title);
    }

    @Test
    public void mostLovedUsesFavoritesAndPlayCount() {
        Track low = track("low", 1, 100L, 10L);
        Track high = track("high", 8, 200L, 20L);
        HashSet<String> favorites = new HashSet<>(Arrays.asList(low.uri, high.uri));

        List<Track> result = new SmartPlaylistResolver().resolve(
                SmartPlaylistDefinition.MOST_LOVED, Arrays.asList(low, high), favorites,
                1_000L, 10);

        assertEquals("high", result.get(0).title);
    }

    private Track track(String title, int plays, long lastPlayed, long added) {
        return new Track(title, "content://music/" + title, title, "Artist", "Album",
                "Artist", "Genre", 0, 0, 0, 100_000, 1L, 1L, "", plays, 0,
                added, lastPlayed, 0L);
    }
}
