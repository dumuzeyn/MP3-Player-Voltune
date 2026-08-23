package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MetadataValidatorTest {
    @Test
    public void normalizesTextAndNumbers() {
        assertEquals("Artist Name", MetadataValidator.cleanText(" Artist\t Name "));
        assertEquals(2026, MetadataValidator.year("2026"));
        assertEquals(7, MetadataValidator.trackNumber("7/12"));
        assertEquals(0, MetadataValidator.year("9999"));
    }
}
