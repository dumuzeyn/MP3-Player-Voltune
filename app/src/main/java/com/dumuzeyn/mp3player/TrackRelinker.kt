package com.dumuzeyn.mp3player

import kotlin.math.abs

/** Matches a newly discovered file to a stable library record using multiple signals. */
object TrackRelinker {
    @JvmStatic
    fun candidates(library: List<Track>, discovered: Track): List<Track> {
        val result = ArrayList<Track>()
        var bestScore = 0
        library.forEach { existing ->
            val score = score(existing, discovered)
            if (score < 8) return@forEach
            if (score > bestScore) {
                bestScore = score
                result.clear()
            }
            if (score == bestScore) result += existing
        }
        return result
    }

    @JvmStatic
    fun score(left: Track, right: Track): Int {
        var score = 0
        if (left.fingerprint.isNotEmpty() && left.fingerprint == right.fingerprint) score += 8
        if (left.fileSize > 0L && left.fileSize == right.fileSize) score += 3
        if (left.durationMs > 0 && abs(left.durationMs - right.durationMs) <= 1_000) score += 2
        if (same(left.title, right.title)) score += 2
        if (same(left.artist, right.artist)) score++
        if (same(left.album, right.album)) score++
        if (left.lastModified > 0L && left.lastModified == right.lastModified) score++
        return score
    }

    private fun same(left: String?, right: String?): Boolean =
        left != null && right != null && left.trim().equals(right.trim(), ignoreCase = true)
}
