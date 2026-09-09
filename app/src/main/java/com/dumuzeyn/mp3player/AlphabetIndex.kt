package com.dumuzeyn.mp3player

import java.util.Locale

internal object AlphabetIndex {
    private val english = ('A'..'Z').map(Char::toString)
    private val russian = listOf(
        "А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н",
        "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь",
        "Э", "Ю", "Я",
    )

    data class Entry(val label: String, val position: Int)

    fun build(titles: List<String>): List<Entry> {
        val firstPositions = LinkedHashMap<String, Int>()
        titles.forEachIndexed { position, title ->
            val key = section(title)
            if (!firstPositions.containsKey(key)) firstPositions[key] = position
        }
        val result = ArrayList<Entry>()
        english.forEach { firstPositions[it]?.let { position -> result += Entry(it, position) } }
        russian.forEach { firstPositions[it]?.let { position -> result += Entry(it, position) } }
        firstPositions[OTHER]?.let { result += Entry(OTHER, it) }
        return result
    }

    fun section(value: String): String {
        val first = value.trimStart().firstOrNull()?.uppercaseChar()?.toString() ?: return OTHER
        return if (first in english || first in russian) first.uppercase(Locale.ROOT) else OTHER
    }

    private const val OTHER = "#"
}
