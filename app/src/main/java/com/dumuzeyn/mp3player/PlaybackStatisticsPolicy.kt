package com.dumuzeyn.mp3player

object PlaybackStatisticsPolicy {
    const val LONG_TRACK_THRESHOLD_MS = 30_000L
    const val SKIP_THRESHOLD_MS = 10_000L

    @JvmStatic
    fun countsAsPlay(listenedMs: Long, durationMs: Long): Boolean {
        if (listenedMs <= 0L) return false
        val required = if (durationMs in 1L..<60_000L) {
            maxOf(5_000L, durationMs / 2L)
        } else {
            LONG_TRACK_THRESHOLD_MS
        }
        return listenedMs >= required
    }

    @JvmStatic
    fun countsAsSkip(listenedMs: Long, durationMs: Long, userInitiated: Boolean): Boolean =
        userInitiated &&
            listenedMs in 0L..<SKIP_THRESHOLD_MS &&
            (durationMs <= 0L || listenedMs < durationMs / 2L)
}
