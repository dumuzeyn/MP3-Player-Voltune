package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LrcParserTest {
    @Test
    public void parsesSortsOffsetsAndMultipleTimestamps() {
        LrcDocument lyrics = new LrcParser().parse(
                "[offset:100]\n[00:10.50]Second\n[00:01.2][00:03]First");

        assertTrue(lyrics.synchronizedLyrics);
        assertEquals(3, lyrics.lines.size());
        assertEquals(1_300L, lyrics.lines.get(0).timeMs);
        assertEquals(1, lyrics.lineAt(3_500L));
    }

    @Test
    public void keepsPlainLyrics() {
        LrcDocument lyrics = new LrcParser().parse("Line one\nLine two");

        assertFalse(lyrics.synchronizedLyrics);
        assertEquals("Line one\nLine two", lyrics.plainText);
    }
}
