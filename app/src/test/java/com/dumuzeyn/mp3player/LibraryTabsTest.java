package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LibraryTabsTest {
    @Test
    public void soundIsImmediatelyAfterPlaylists() {
        assertEquals(LibraryTabs.PLAYLISTS + 1, LibraryTabs.SOUND);
        assertEquals(LibraryTabs.SOUND + 1, LibraryTabs.GENRES);
        assertEquals(9, LibraryTabs.SETTINGS);
    }
}
