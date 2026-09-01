package com.dumuzeyn.mp3player

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager

/** Service-owned playback metadata layered on top of the authoritative Media3 player. */
class PlaybackServiceState(
    private val player: Player,
    private val mapper: MediaItemMapper,
    private val stateManager: PlaybackStateManager,
) {
    var pauseReason: PauseReason = PauseReason.NONE
    var stopReason: StopReason = StopReason.NONE
    var lastError: PlaybackErrorInfo? = null

    fun currentTrack(): Track? = mapper.fromMediaItem(player.currentMediaItem)

    fun currentUri(): String = player.currentMediaItem
        ?.localConfiguration
        ?.uri
        ?.toString()
        .orEmpty()

    fun currentMediaId(): String = player.currentMediaItem?.mediaId.orEmpty()

    fun persist(includeQueue: Boolean) {
        stateManager.save(snapshot(), currentUri(), includeQueue)
    }

    fun snapshot(): PlaybackSnapshot = PlaybackSnapshot(
        buildList(player.mediaItemCount) {
            repeat(player.mediaItemCount) { index -> add(player.getMediaItemAt(index).mediaId) }
        },
        currentMediaId(),
        player.currentMediaItemIndex,
        player.currentPosition,
        player.duration,
        player.playWhenReady,
        player.playbackState,
        player.repeatMode,
        false,
        phase(),
        pauseReason,
        stopReason,
        lastError,
        System.currentTimeMillis(),
    )

    fun snapshotBundle(): Bundle = snapshot().let { value ->
        Bundle().apply {
            putString("mediaId", value.currentMediaId)
            putInt("index", value.currentIndex)
            putLong("position", value.positionMs)
            putLong("duration", value.durationMs)
            putString("phase", value.phase.name)
            putString("pauseReason", value.pauseReason.name)
            putString("stopReason", value.stopReason.name)
        }
    }

    private fun phase(): PlaybackPhase {
        if (lastError != null && player.playbackState == Player.STATE_IDLE) {
            return PlaybackPhase.ERROR
        }
        return when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
            Player.STATE_READY -> PlaybackPhase.READY
            Player.STATE_ENDED -> PlaybackPhase.ENDED
            else -> PlaybackPhase.IDLE
        }
    }

    companion object {
        @JvmStatic
        fun safeInt(value: Long): Int = when {
            value == C.TIME_UNSET || value < 0L -> 0
            else -> value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
