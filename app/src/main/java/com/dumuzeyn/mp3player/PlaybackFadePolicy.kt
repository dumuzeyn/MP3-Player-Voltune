package com.dumuzeyn.mp3player

internal object PlaybackFadePolicy {
    const val PREFS = "playback_fade"
    const val ENABLED = "enabled"
    const val SECONDS = "seconds"
    const val DEFAULT_SECONDS = 3

    fun gain(positionMs: Long, durationMs: Long, fadeMs: Long, speed: Float): Float {
        if (durationMs <= 0L || positionMs < 0L || fadeMs <= 0L) return 1f
        val safeSpeed = PlaybackSpeedPolicy.constrain(speed)
        // Never fade more than the last half of a short clip.
        val window = minOf(fadeMs.toDouble() * safeSpeed, durationMs / 2.0)
        if (window <= 0.0) return 1f
        val remaining = (durationMs - positionMs).coerceAtLeast(0)
        return (remaining / window).coerceIn(0.0, 1.0).toFloat()
    }
}
