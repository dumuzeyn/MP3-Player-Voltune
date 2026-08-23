package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackEventLoggerTest {
    @Test
    public void legacyUriIsReplacedWithOpaqueIdentifier() {
        String id = PlaybackEventLogger.opaqueMediaId(
                "content://media/external/audio/media/private-song-name.mp3");

        assertTrue(id.startsWith("legacy-"));
        assertFalse(id.contains("content"));
        assertFalse(id.contains("private-song-name"));
    }
}
