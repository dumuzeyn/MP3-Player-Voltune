package com.dumuzeyn.mp3player

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Trace
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager
import com.dumuzeyn.mp3player.playback.service.PlaybackErrorRecovery
import com.dumuzeyn.mp3player.playback.service.PlaybackSleepTimer
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("UnsafeOptInUsageError")
class Media3PlayerService : MediaLibraryService() {
    private val mapper = MediaItemMapper()
    private val transitionPolicy = PlaybackTransitionPolicy()
    private val errorRecovery = PlaybackErrorRecovery()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var artworkProvider: MediaArtworkProvider
    private lateinit var stateManager: PlaybackStateManager
    private lateinit var sleepTimer: PlaybackSleepTimer
    private lateinit var audioEffects: AudioEffectsManager
    private lateinit var loudnessNormalizer: TrackLoudnessNormalizer
    private lateinit var commandHandler: Media3SessionCommandHandler
    private lateinit var eventLogger: PlaybackEventLogger
    private lateinit var historyRecorder: PlaybackHistoryRecorder
    private lateinit var sessionRestorer: PlaybackSessionRestorer
    private lateinit var libraryCallback: VoltuneMediaLibraryCallback
    private lateinit var playbackState: PlaybackServiceState
    private var positionSaveJob: Job? = null
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var audioFocusState = "managed"

    override fun onCreate() {
        super.onCreate()
        stateManager = PlaybackStateManager(this)
        sleepTimer = PlaybackSleepTimer(this, ::onSleepTimerExpired)
        audioEffects = AudioEffectsManager(this)
        loudnessNormalizer = TrackLoudnessNormalizer(this)
        artworkProvider = MediaArtworkProvider(this)
        eventLogger = PlaybackEventLogger(this)
        historyRecorder = PlaybackHistoryRecorder(this)

        val uninterrupted = getSharedPreferences(UninterruptedPlaybackController.PREFS, MODE_PRIVATE)
            .getBoolean(UninterruptedPlaybackController.ENABLED, false)
        audioFocusState = if (uninterrupted) "ignored_by_setting" else "managed"
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(attributes, !uninterrupted)
            .setHandleAudioBecomingNoisy(!uninterrupted)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        playbackState = PlaybackServiceState(player, mapper, stateManager)
        player.addListener(PlayerEvents())

        commandHandler = Media3SessionCommandHandler(
            player,
            sleepTimer,
            stateManager,
            ::applyAudioEffects,
            { playbackState.stopReason = StopReason.USER },
            playbackState::snapshotBundle,
        )
        libraryCallback = VoltuneMediaLibraryCallback(
            LibraryDatabase(this),
            mapper,
            object : VoltuneMediaLibraryCallback.CommandDelegate {
                override fun handle(
                    command: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> = commandHandler.handle(command, args)

                override fun onCommand(action: String) {
                    val separator = action.lastIndexOf('.')
                    logEvent("command_${action.substring(separator + 1).lowercase()}", "none")
                }
            },
        )
        mediaSession = MediaLibrarySession.Builder(this, player, libraryCallback)
            .setBitmapLoader(artworkProvider)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_music)
        setMediaNotificationProvider(notificationProvider)

        sessionRestorer = PlaybackSessionRestorer(this, stateManager, mapper)
        sessionRestorer.restore(player)
        sleepTimer.restore()
        PlayerWidgetProvider.updateFromPlayer(this, player)
        logEvent("service_created", "none")
        VoltuneLog.info("service_created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        logEvent("task_removed", "none")
        if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) stopSelf()
    }

    override fun onDestroy() {
        stopPositionSaver()
        sleepTimer.close()
        if (playbackState.stopReason == StopReason.NONE) {
            playbackState.stopReason = StopReason.SERVICE_DESTROYED
        }
        playbackState.persist(true)
        logEvent("service_destroyed", "none", false)
        mediaSession.release()
        player.release()
        audioEffects.release()
        loudnessNormalizer.release()
        artworkProvider.close()
        historyRecorder.close()
        sessionRestorer.close()
        libraryCallback.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onSleepTimerExpired() {
        playbackState.pauseReason = PauseReason.SLEEP_TIMER
        playbackState.stopReason = StopReason.SLEEP_TIMER
        player.pause()
        player.stop()
        playbackState.persist(true)
        logEvent("sleep_timer_expired", "none")
    }

    private fun applyAudioEffects() {
        val analyzedGain = if (loudnessNormalizer.isEnabled) {
            loudnessNormalizer.cachedGainDb(playbackState.currentTrack())
        } else {
            0.0f
        }
        val appliedGain = audioEffects.adjustedNormalizationGainDb(analyzedGain)
        player.volume = AudioEffectsManager.playerVolumeForGainDb(appliedGain)
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            audioEffects.apply(audioSessionId, appliedGain.coerceAtLeast(0.0f))
        }
    }

    private fun prefetchLoudness() {
        if (!loudnessNormalizer.isEnabled || player.mediaItemCount == 0) return
        val upcoming = ArrayList<Track>(3)
        val start = player.currentMediaItemIndex.coerceAtLeast(0)
        repeat(minOf(3, player.mediaItemCount)) { offset ->
            mapper.fromMediaItem(player.getMediaItemAt((start + offset) % player.mediaItemCount))
                ?.let(upcoming::add)
        }
        loudnessNormalizer.prefetch(upcoming, 0)
    }

    private fun recoverFromError(error: PlaybackException) {
        val failures = errorRecovery.recordError()
        val queueSize = player.mediaItemCount
        val recoverable = queueSize > 1
        playbackState.lastError = PlaybackErrorInfo(
            error.errorCode,
            error.errorCodeName,
            recoverable,
            error.message,
            playbackState.currentMediaId(),
        )
        logEvent("player_error", playbackState.lastError?.category ?: "unknown")
        if (transitionPolicy.shouldSkipError(failures, queueSize, recoverable)) {
            val next = (player.currentMediaItemIndex.coerceAtLeast(0) + 1) % queueSize
            player.seekToDefaultPosition(next)
            player.prepare()
            player.play()
            return
        }
        playbackState.stopReason = transitionPolicy.stopReasonForError(
            failures,
            queueSize,
            recoverable,
        )
        player.stop()
        playbackState.persist(true)
    }

    private fun updateDurationAsync(uri: String, durationMs: Int) {
        if (uri.isEmpty()) return
        serviceScope.launch(Dispatchers.IO) {
            TrackStore.updateDuration(this@Media3PlayerService, uri, durationMs)
        }
    }

    private fun startPositionSaver() {
        stopPositionSaver()
        positionSaveJob = serviceScope.launch {
            delay(POSITION_SAVE_INTERVAL_MS)
            while (isActive && player.isPlaying) {
                historyRecorder.sample(player.duration)
                playbackState.persist(false)
                delay(POSITION_SAVE_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionSaver() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    private fun logEvent(type: String, errorCategory: String) {
        val foreground = player.isPlaying ||
            (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        logEvent(type, errorCategory, foreground)
    }

    private fun logEvent(type: String, errorCategory: String, foreground: Boolean) {
        eventLogger.record(
            type,
            playbackState.snapshot(),
            errorCategory,
            audioFocusState,
            true,
            foreground,
        )
    }

    private inline fun traced(section: String, action: () -> Unit) {
        Trace.beginSection(section)
        try {
            action()
        } finally {
            Trace.endSection()
        }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) =
            traced("Voltune/Playback.isPlayingChanged") {
                historyRecorder.playing(isPlaying)
                stopPositionSaver()
                if (isPlaying) {
                    playbackState.pauseReason = PauseReason.NONE
                    playbackState.stopReason = StopReason.NONE
                    playbackState.lastError = null
                    errorRecovery.resetConsecutiveErrors()
                    transitionPolicy.onUserPlay()
                    prefetchLoudness()
                    startPositionSaver()
                }
                playbackState.persist(true)
                PlayerWidgetProvider.updateFromPlayer(this@Media3PlayerService, player)
                logEvent(if (isPlaying) "playback_started" else "playback_paused", "none")
            }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                when {
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                        playbackState.pauseReason = transitionPolicy.onTemporaryAudioFocusLoss(true)
                        audioFocusState = "lost"
                    }
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                        playbackState.pauseReason = transitionPolicy.onAudioBecomingNoisy()
                        audioFocusState = "audio_becoming_noisy"
                    }
                    playbackState.pauseReason == PauseReason.NONE -> {
                        playbackState.pauseReason = PauseReason.USER
                        transitionPolicy.onUserPause()
                        audioFocusState = "inactive"
                    }
                }
            } else {
                audioFocusState = "active_or_not_required"
            }
            playbackState.persist(false)
            logEvent("play_when_ready_changed", "none")
        }

        override fun onPlaybackStateChanged(state: Int) =
            traced("Voltune/Playback.stateChanged") {
                if (state == Player.STATE_READY) {
                    errorRecovery.resetConsecutiveErrors()
                    historyRecorder.sample(player.duration)
                    updateDurationAsync(
                        playbackState.currentUri(),
                        PlaybackServiceState.safeInt(player.duration),
                    )
                } else if (state == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF) {
                    playbackState.stopReason = StopReason.QUEUE_ENDED
                    historyRecorder.ended(player.duration)
                }
                playbackState.persist(true)
                PlayerWidgetProvider.updateFromPlayer(this@Media3PlayerService, player)
                logEvent("playback_state_changed", "none")
            }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
            traced("Voltune/Playback.mediaItemTransition") {
                historyRecorder.transition(mediaItem?.mediaId.orEmpty(), player.duration, reason)
                audioEffects.release()
                errorRecovery.resetConsecutiveErrors()
                playbackState.lastError = null
                prefetchLoudness()
                applyAudioEffects()
                playbackState.persist(true)
                PlayerWidgetProvider.updateFromPlayer(this@Media3PlayerService, player)
                logEvent("media_item_transition", "none")
            }

        override fun onPlayerError(error: PlaybackException) = recoverFromError(error)

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            this@Media3PlayerService.audioSessionId = audioSessionId
            applyAudioEffects()
            logEvent("audio_session_changed", "none")
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            logEvent("repeat_mode_changed", "none")
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            logEvent("shuffle_mode_changed", "none")
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 7
        const val POSITION_SAVE_INTERVAL_MS = 7_000L
    }
}
