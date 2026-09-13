package com.dumuzeyn.mp3player

import java.util.Collections

class LrcDocument(lines: List<LrcLine>, plainText: String?, @JvmField val synchronizedLyrics: Boolean) {
    @JvmField val lines: List<LrcLine> = Collections.unmodifiableList(ArrayList(lines))
    @JvmField val plainText: String = plainText.orEmpty()

    fun lineAt(positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].timeMs <= positionMs) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }
}
