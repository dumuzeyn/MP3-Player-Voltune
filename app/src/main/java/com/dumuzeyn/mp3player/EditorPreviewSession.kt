package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager
import java.io.File

/** Temporarily borrows the service player without replacing its persisted music session. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class EditorPreviewSession(
    private val context: Context,
    private val player: Player,
    private val changed: (Boolean) -> Unit,
) : Player.Listener, AutoCloseable {
    private data class Saved(val items: List<MediaItem>, val index: Int, val position: Long,
        val playing: Boolean, val repeat: Int, val shuffle: Boolean, val speed: PlaybackParameters)
    private val handler = Handler(player.applicationLooper)
    private var saved: Saved? = null
    private var owner: MediaSession.ControllerInfo? = null
    private var token = ""
    private var error = false
    val active get() = saved != null

    init { player.addListener(this) }

    fun command(controller: MediaSession.ControllerInfo, args: Bundle): SessionResult {
        return try {
            when (args.getString("action")) {
                "start" -> start(controller, args)
                "stop" -> if (owner == controller) stop()
                "toggle" -> if (owner == controller && active) player.playWhenReady = !player.playWhenReady
                "seek" -> if (owner == controller && active) player.seekTo(args.getLong("position").coerceAtLeast(0))
                "state" -> Unit
                else -> return SessionResult(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
            }
            SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                putBoolean("active", active && owner == controller)
                putBoolean("playing", active && player.playWhenReady)
                putBoolean("error", error)
                putString("token", token)
                putLong("position", if (active) player.currentPosition.coerceAtLeast(0) else 0)
                putLong("duration", if (active) player.duration.coerceAtLeast(0) else 0)
            })
        } catch (_: Exception) {
            if (owner == controller) stop()
            SessionResult(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
        }
    }

    private fun start(controller: MediaSession.ControllerInfo, args: Bundle) {
        val file = File(checkNotNull(args.getString("path"))).canonicalFile
        require(file.parentFile == context.cacheDir.canonicalFile &&
            file.name.startsWith("voltune-edit-") && file.extension == "m4a" && file.isFile)
        val requestedToken = checkNotNull(args.getString("token")).also { require(it.isNotBlank()) }
        stop()
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val backup = Saved(items, player.currentMediaItemIndex.coerceAtLeast(0),
            player.currentPosition.coerceAtLeast(0), player.playWhenReady, player.repeatMode,
            player.shuffleModeEnabled, player.playbackParameters)
        val state = PlaybackStateManager(context)
        if (items.isEmpty()) state.clear() else state.save(PlaybackStateManager.Snapshot(
            player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty(),
            PlaybackServiceState.safeInt(backup.position), PlaybackServiceState.safeInt(player.duration),
            backup.index, RepeatModeMapper.fromMedia3(backup.repeat), false, backup.shuffle,
            ArrayList(items.map { it.mediaId })), true)
        saved = backup
        owner = controller
        token = requestedToken
        error = false
        changed(true)
        player.pause()
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        player.playbackParameters = PlaybackParameters.DEFAULT
        player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(file)).setMediaId(PREFIX + token)
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Voltune: " +
                args.getString("title", "Preview")).build()).build())
        player.prepare()
        player.play()
    }

    fun stop(resume: Boolean = true) {
        val backup = saved ?: return
        handler.removeCallbacksAndMessages(null)
        player.pause()
        player.stop()
        if (backup.items.isEmpty()) player.clearMediaItems() else {
            player.setMediaItems(backup.items, backup.index.coerceAtMost(backup.items.lastIndex), backup.position)
        }
        player.repeatMode = backup.repeat
        player.shuffleModeEnabled = backup.shuffle
        player.playbackParameters = backup.speed
        if (backup.items.isNotEmpty()) {
            player.prepare()
            player.playWhenReady = resume && backup.playing
        }
        saved = null
        owner = null
        changed(false)
    }

    fun disconnected(controller: MediaSession.ControllerInfo) { if (owner == controller) stop() }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!active) return
        if (player.playerError != null || player.playbackState == Player.STATE_ENDED) {
            error = player.playerError != null
            handler.post { stop() }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (active && !playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) {
            handler.post { stop(false) }
        }
    }

    override fun close() { stop(false); handler.removeCallbacksAndMessages(null); player.removeListener(this) }

    companion object {
        private const val PREFIX = "voltune.editor.preview:"
        fun isPreview(item: MediaItem?) = item?.mediaId?.startsWith(PREFIX) == true
    }
}
