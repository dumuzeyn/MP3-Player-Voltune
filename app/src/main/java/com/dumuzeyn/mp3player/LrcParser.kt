package com.dumuzeyn.mp3player

import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.min

internal class LrcParser {
    fun parse(input: String?): LrcDocument {
        val source = input.orEmpty()
        val safe = source.substring(0, min(source.length, MAX_INPUT_CHARS))
        val offset = parseOffset(safe)
        val timed = ArrayList<LrcLine>()
        val plain = StringBuilder()
        val rows = safe.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for (index in 0 until min(rows.size, MAX_LINES)) {
            parseRow(rows[index], offset, timed, plain)
        }
        timed.sortWith { left, right -> left.timeMs.compareTo(right.timeMs) }
        return LrcDocument(timed, plain.toString().trim(), timed.isNotEmpty())
    }

    private fun parseRow(
        row: String,
        offset: Long,
        timed: ArrayList<LrcLine>,
        plain: StringBuilder,
    ) {
        val matcher = TIMESTAMP.matcher(row)
        val times = ArrayList<Long>()
        var textStart = 0
        while (matcher.find()) {
            times.add((timestamp(matcher) + offset).coerceAtLeast(0L))
            textStart = matcher.end()
        }
        val text = row.substring(min(textStart, row.length)).trim()
        if (times.isEmpty()) {
            if (!row.startsWith("[") && row.isNotBlank()) appendPlain(plain, row.trim())
            return
        }
        times.forEach { time -> timed.add(LrcLine(time, text)) }
        appendPlain(plain, text)
    }

    private fun timestamp(matcher: Matcher): Long {
        val minutes = number(matcher.group(1).orEmpty())
        val seconds = min(59L, number(matcher.group(2).orEmpty()))
        val fraction = matcher.group(3)
        var millis = 0L
        if (fraction != null) {
            millis = number(fraction)
            if (fraction.length == 1) millis *= 100L else if (fraction.length == 2) millis *= 10L
        }
        return minutes * 60_000L + seconds * 1_000L + min(999L, millis)
    }

    private fun parseOffset(source: String): Long {
        val matcher = OFFSET.matcher(source)
        return if (matcher.find()) number(matcher.group(1).orEmpty()) else 0L
    }

    private fun number(value: String): Long = try {
        value.toLong()
    } catch (_: NumberFormatException) {
        0L
    }

    private fun appendPlain(target: StringBuilder, value: String) {
        if (value.isEmpty()) return
        if (target.isNotEmpty()) target.append('\n')
        target.append(value)
    }

    private companion object {
        const val MAX_INPUT_CHARS = 1_000_000
        const val MAX_LINES = 10_000
        val TIMESTAMP: Pattern = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
        val OFFSET: Pattern = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE)
    }
}
