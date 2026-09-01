package com.dumuzeyn.mp3player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Persists service-owned listening statistics without blocking Media3 callbacks. */
class PlaybackHistoryRecorder(context: Context) : AutoCloseable {
    private val context = context.applicationContext
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val tracker = PlaybackHistoryTracker(
        object : PlaybackHistoryTracker.Listener {
            override fun onPlayed(trackId: String, completed: Boolean, timestamp: Long) {
                execute { it.recordPlayed(trackId, completed, timestamp) }
            }

            override fun onSkipped(trackId: String, timestamp: Long) {
                execute { it.recordSkipped(trackId, timestamp) }
            }
        },
    )
    private var closed = false

    fun transition(mediaId: String, durationMs: Long, reason: Int) {
        tracker.transitionTo(
            mediaId,
            safeDuration(durationMs),
            System.currentTimeMillis(),
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        )
    }

    fun playing(value: Boolean) = tracker.setPlaying(value, System.currentTimeMillis())

    fun sample(durationMs: Long) {
        tracker.updateDuration(safeDuration(durationMs))
        tracker.sample(System.currentTimeMillis())
    }

    fun ended(durationMs: Long) {
        tracker.updateDuration(safeDuration(durationMs))
        tracker.sample(System.currentTimeMillis())
        tracker.finish(false, System.currentTimeMillis())
    }

    override fun close() {
        if (closed) return
        closed = true
        tracker.sample(System.currentTimeMillis())
        tracker.finish(false, System.currentTimeMillis())
        job.complete()
    }

    private fun execute(action: (LibraryDatabase) -> Unit) {
        if (closed) return
        scope.launch {
            LibraryDatabase(context).use { database ->
                action(database)
                LibraryContentVersion.bump(context)
            }
        }
    }

    private fun safeDuration(value: Long): Long =
        if (value == C.TIME_UNSET) 0L else value.coerceAtLeast(0L)
}
