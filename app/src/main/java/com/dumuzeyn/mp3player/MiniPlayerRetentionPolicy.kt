package com.dumuzeyn.mp3player

/** Defines how long a stopped or paused session remains visible in the mini-player. */
object MiniPlayerRetentionPolicy {
    @JvmStatic
    fun isPlaybackActive(
        playWhenReady: Boolean,
        phase: PlaybackPhase,
        stopReason: StopReason,
    ): Boolean = playWhenReady && phase != PlaybackPhase.ENDED && stopReason != StopReason.QUEUE_ENDED

    @JvmStatic
    fun isExpired(
        playing: Boolean,
        inactiveSince: Long,
        legacySavedAt: Long,
        now: Long,
        retentionMs: Long,
    ): Boolean {
        if (playing) return false
        if (retentionMs <= 0L) return true
        val startedAt = inactiveSince.takeIf { it > 0L } ?: legacySavedAt
        return startedAt <= 0L || now - startedAt > retentionMs
    }
}
