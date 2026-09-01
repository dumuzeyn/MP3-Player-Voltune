package com.dumuzeyn.mp3player

class PlaybackTransitionPolicy {
    private var wasPlayingBeforeFocusLoss = false
    private var userPausedAfterFocusLoss = false

    fun onUserPause() {
        userPausedAfterFocusLoss = true
    }

    fun onUserPlay() {
        userPausedAfterFocusLoss = false
    }

    fun onTemporaryAudioFocusLoss(wasPlaying: Boolean): PauseReason {
        wasPlayingBeforeFocusLoss = wasPlaying
        userPausedAfterFocusLoss = false
        return PauseReason.AUDIO_FOCUS
    }

    fun shouldResumeAfterAudioFocusGain(): Boolean {
        val resume = wasPlayingBeforeFocusLoss && !userPausedAfterFocusLoss
        wasPlayingBeforeFocusLoss = false
        return resume
    }

    fun onAudioBecomingNoisy(): PauseReason {
        wasPlayingBeforeFocusLoss = false
        return PauseReason.AUDIO_BECOMING_NOISY
    }

    fun shouldSkipError(consecutiveErrors: Int, queueSize: Int, recoverable: Boolean): Boolean =
        recoverable && queueSize > 1 && consecutiveErrors < queueSize

    fun stopReasonForError(
        consecutiveErrors: Int,
        queueSize: Int,
        recoverable: Boolean,
    ): StopReason = if (recoverable && queueSize > 0 && consecutiveErrors >= queueSize) {
        StopReason.ALL_ITEMS_UNAVAILABLE
    } else {
        StopReason.FATAL_ERROR
    }
}
