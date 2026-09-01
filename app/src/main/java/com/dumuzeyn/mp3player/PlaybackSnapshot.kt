package com.dumuzeyn.mp3player

import java.util.Collections

/** Immutable projection of the authoritative Media3 player state. */
class PlaybackSnapshot(
    queueMediaIds: List<String>?,
    currentMediaId: String?,
    @JvmField val currentIndex: Int,
    positionMs: Long,
    durationMs: Long,
    @JvmField val playWhenReady: Boolean,
    @JvmField val playbackState: Int,
    @JvmField val repeatMode: Int,
    @JvmField val shuffleEnabled: Boolean,
    phase: PlaybackPhase?,
    pauseReason: PauseReason?,
    stopReason: StopReason?,
    @JvmField val lastError: PlaybackErrorInfo?,
    @JvmField val updatedAt: Long,
) {
    @JvmField
    val queueMediaIds: List<String> = Collections.unmodifiableList(
        ArrayList(queueMediaIds.orEmpty()),
    )

    @JvmField val currentMediaId: String = currentMediaId.orEmpty()
    @JvmField val positionMs: Long = positionMs.coerceAtLeast(0L)
    @JvmField val durationMs: Long = durationMs.coerceAtLeast(0L)
    @JvmField val phase: PlaybackPhase = phase ?: PlaybackPhase.IDLE
    @JvmField val pauseReason: PauseReason = pauseReason ?: PauseReason.NONE
    @JvmField val stopReason: StopReason = stopReason ?: StopReason.NONE

    companion object {
        @JvmStatic
        fun empty(): PlaybackSnapshot = PlaybackSnapshot(
            emptyList(),
            "",
            -1,
            0L,
            0L,
            false,
            1,
            0,
            false,
            PlaybackPhase.IDLE,
            PauseReason.NONE,
            StopReason.NONE,
            null,
            System.currentTimeMillis(),
        )
    }
}
