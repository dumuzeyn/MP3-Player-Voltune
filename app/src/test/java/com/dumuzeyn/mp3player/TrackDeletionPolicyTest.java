package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TrackDeletionPolicyTest {
    @Test
    public void recognizesMediaStoreAudio() {
        assertTrue(TrackDeletionPolicy.isMediaStore(
                "content://media/external/audio/media/42"));
    }

    @Test
    public void rejectsFileAndForeignContentAsMediaStore() {
        assertFalse(TrackDeletionPolicy.isMediaStore("file:///music/song.mp3"));
        assertFalse(TrackDeletionPolicy.isMediaStore(
                "content://downloads/public_downloads/42"));
    }
}
