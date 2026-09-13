package com.dumuzeyn.mp3player

/** Counts listening time from the service process, independently of Activity connections. */
class PlaybackHistoryTracker(private val listener: Listener) {
    interface Listener {
        fun onPlayed(trackId: String, completed: Boolean, timestamp: Long)
        fun onSkipped(trackId: String, timestamp: Long)
    }

    private var trackId = ""
    private var durationMs = 0L
    private var listenedMs = 0L
    private var lastSampleAt = 0L
    private var playing = false
    private var counted = false

    fun transitionTo(
        newTrackId: String?,
        newDurationMs: Long,
        now: Long,
        userInitiated: Boolean,
    ) {
        sample(now)
        finish(userInitiated, now)
        trackId = newTrackId.orEmpty()
        durationMs = newDurationMs.coerceAtLeast(0L)
        listenedMs = 0L
        counted = false
        lastSampleAt = now
    }

    fun setPlaying(value: Boolean, now: Long) {
        sample(now)
        playing = value
        lastSampleAt = now
    }

    fun sample(now: Long) {
        if (playing && trackId.isNotEmpty() && lastSampleAt > 0L) {
            listenedMs += (now - lastSampleAt).coerceIn(0L, MAX_SAMPLE_GAP_MS)
            if (!counted && PlaybackStatisticsPolicy.countsAsPlay(listenedMs, durationMs)) {
                counted = true
                listener.onPlayed(trackId, false, now)
            }
        }
        lastSampleAt = now
    }

    fun updateDuration(value: Long) {
        if (value > 0L) durationMs = value
    }

    fun finish(userInitiated: Boolean, now: Long) {
        if (trackId.isEmpty()) return
        if (counted) {
            if (durationMs > 0L && listenedMs >= durationMs * 9L / 10L) {
                listener.onPlayed(trackId, true, now)
            }
        } else if (PlaybackStatisticsPolicy.countsAsSkip(listenedMs, durationMs, userInitiated)) {
            listener.onSkipped(trackId, now)
        }
        trackId = ""
        playing = false
    }

    fun listenedMs(): Long = listenedMs

    private companion object {
        const val MAX_SAMPLE_GAP_MS = 15_000L
    }
}
