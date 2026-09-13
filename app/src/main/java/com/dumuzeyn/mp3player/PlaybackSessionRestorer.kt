package com.dumuzeyn.mp3player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Restores persisted state into an empty Media3 player after process recreation. */
class PlaybackSessionRestorer(
    context: Context,
    private val stateManager: PlaybackStateManager,
    private val mapper: MediaItemMapper,
) : AutoCloseable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun restore(player: Player) {
        val state = stateManager.load()
        if (state.uri.isEmpty() && state.queueUris.isEmpty()) return
        val retentionMs = UiPreferencesStore.readResumeWindowMinutes(context) * 60_000L
        if (
            MiniPlayerRetentionPolicy.isExpired(
                state.playing,
                state.inactiveSince,
                state.savedAt,
                System.currentTimeMillis(),
                retentionMs,
            )
        ) {
            stateManager.clear()
            return
        }
        scope.launch {
            val items = PlaybackQueueResolver.restore(
                TrackStore.load(context),
                state.queueUris,
                null,
            ).mapTo(ArrayList<MediaItem>(), mapper::toMediaItem)
            withContext(Dispatchers.Main.immediate) {
                apply(player, state, items)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    private fun apply(
        player: Player,
        state: PlaybackStateManager.State,
        items: ArrayList<MediaItem>,
    ) {
        if (items.isEmpty() || player.mediaItemCount > 0) return
        val index = state.index.coerceIn(0, items.lastIndex)
        player.setMediaItems(items, index, state.position.coerceAtLeast(0).toLong())
        player.repeatMode = RepeatModeMapper.toMedia3(state.loopMode)
        player.shuffleModeEnabled = false
        player.prepare()
        if (state.playing) player.play()
    }
}
