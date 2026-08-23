package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FolderGroupingTest {
    @Test
    public void extractsParentWithoutResolvingFilesystemPath() {
        assertEquals("Album", FolderGrouping.folderName(
                "content://provider/tree/Music/Album/song.mp3"));
        assertEquals("Unknown folder", FolderGrouping.folderName(""));
    }
}
