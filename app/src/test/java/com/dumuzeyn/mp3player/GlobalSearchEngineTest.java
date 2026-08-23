package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class GlobalSearchEngineTest {
    @Test
    public void searchNormalizesCyrillicCaseAndWhitespaceAcrossCategories() {
        Track track = new Track("content://music/one", "Белая ночь", "Виктор",
                "Город", "Рок");
        Playlist playlist = new Playlist("Любимый РОК");

        GlobalSearchResult songResult = new GlobalSearchEngine().search(
                Arrays.asList(track), Arrays.asList(playlist), "  БЕЛАЯ   НОЧЬ ", 8);
        GlobalSearchResult groupResult = new GlobalSearchEngine().search(
                Arrays.asList(track), Arrays.asList(playlist), "  рок ", 0);

        assertEquals(1, songResult.songs.size());
        assertEquals("Белая ночь", songResult.songs.get(0).title);
        assertTrue(groupResult.genres.contains("Рок"));
        assertEquals(1, groupResult.playlists.size());
    }

    @Test
    public void categoryLimitDoesNotScanResultsPastRequestedSize() {
        Track first = new Track("content://music/1", "One", "Same artist");
        Track second = new Track("content://music/2", "Two", "Same artist");
        GlobalSearchResult result = new GlobalSearchEngine().search(
                Arrays.asList(first, second), Arrays.asList(), "same", 1);
        assertEquals(1, result.songs.size());
        assertEquals(1, result.artists.size());
    }
}
