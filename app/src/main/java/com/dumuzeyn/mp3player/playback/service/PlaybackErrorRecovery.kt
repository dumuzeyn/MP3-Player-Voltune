package com.dumuzeyn.mp3player.playback.service

/** Tracks bounded playback retries independently from the Media3 lifecycle. */
class PlaybackErrorRecovery {
    private var consecutiveErrorsValue = 0
    private var repeatOneRetriesValue = 0

    fun recordError(): Int = ++consecutiveErrorsValue
    fun consecutiveErrors(): Int = consecutiveErrorsValue
    fun exhausted(queueSize: Int): Boolean = queueSize <= 0 || consecutiveErrorsValue >= queueSize
    fun resetConsecutiveErrors() {
        consecutiveErrorsValue = 0
    }
    fun repeatOneRetries(): Int = repeatOneRetriesValue
    fun setRepeatOneRetries(retries: Int) {
        repeatOneRetriesValue = retries.coerceAtLeast(0)
    }
    fun resetRepeatOneRetries() {
        repeatOneRetriesValue = 0
    }
}
