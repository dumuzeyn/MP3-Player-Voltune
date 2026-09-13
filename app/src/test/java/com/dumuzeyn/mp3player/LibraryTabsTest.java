package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LibraryTabsTest {
    @Test
    public void soundIsImmediatelyAfterPlaylists() {
        assertEquals(LibraryTabs.PLAYLISTS + 1, LibraryTabs.SOUND);
        assertEquals(LibraryTabs.SOUND + 1, LibraryTabs.GENRES);
        assertEquals(LibraryTabs.FOLDERS + 1, LibraryTabs.EDITOR);
        assertEquals(LibraryTabs.EDITOR + 1, LibraryTabs.SETTINGS);
    }
}
