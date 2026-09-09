package com.dumuzeyn.mp3player

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetIndexTest {
    @Test fun latinAlwaysPrecedesCyrillicAndPositionsPointToFirstTrack() {
        val index = AlphabetIndex.build(listOf("Яблоко", "beta", "Alpha", "Блюз", "another", "42"))
        assertEquals(listOf("A", "B", "Б", "Я", "#"), index.map { it.label })
        assertEquals(listOf(2, 1, 3, 0, 5), index.map { it.position })
    }

    @Test fun englishOnlyTitlesDoNotCreateSecondAlphabet() {
        assertEquals(listOf("A", "Z"), AlphabetIndex.build(listOf("Alpha", "Zulu")).map { it.label })
    }

    @Test fun whitespaceCaseAndYoAreNormalized() {
        assertEquals("Ё", AlphabetIndex.section("  ёлка"))
        assertEquals("A", AlphabetIndex.section("apple"))
        assertEquals("#", AlphabetIndex.section(""))
    }
}
