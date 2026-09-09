package com.dumuzeyn.mp3player

internal object PlaybackSpeedPolicy {
    const val MIN = 0.25f
    const val MAX = 4f
    const val STEPS = 75

    fun constrain(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN, MAX) else 1f
    fun fromProgress(progress: Int): Float = MIN + progress.coerceIn(0, STEPS) * 0.05f
    fun progress(value: Float): Int = kotlin.math.round((constrain(value) - MIN) / 0.05f).toInt()
    fun toggle(current: Float, selected: Float): Float =
        if (kotlin.math.abs(current - 1f) < 0.001f) constrain(selected) else 1f
}
