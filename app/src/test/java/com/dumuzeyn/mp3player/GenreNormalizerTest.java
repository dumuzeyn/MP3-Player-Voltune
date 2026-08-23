package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GenreNormalizerTest {
    @Test
    public void normalizesPlaceholdersWithoutDestroyingCustomGenres() {
        assertEquals(GenreNormalizer.UNKNOWN, GenreNormalizer.normalize("  unknown  "));
        assertEquals(GenreNormalizer.UNKNOWN, GenreNormalizer.normalize("<unknown>"));
        assertEquals("Dark Folk / Neo Folk",
                GenreNormalizer.normalize("  Dark Folk / Neo   Folk  "));
        assertFalse(GenreNormalizer.isUnknown("Авторская песня"));
    }

    @Test
    public void resolvesId3v1AndTconNumericValues() {
        assertEquals("Rock", GenreNormalizer.normalize("17"));
        assertEquals("Rock", GenreNormalizer.normalize("(17)"));
        assertEquals("Alternative Rock", GenreNormalizer.normalize("(17)Alternative Rock"));
        assertTrue(GenreNormalizer.isUnknown("255"));
    }
}
