package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.sqrt

/** Draws live playback levels only while the full player is visible. */
internal class FullPlayerVisualizerView(private val host: MainActivityCore) : View(host), AutoCloseable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = false

    init {
        contentDescription = host.tr("Audio visualizer", "Аудиовизуализатор")
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        PlaybackVisualizerBuffer.shared.setEnabled(value)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pitch = host.dp(6).toFloat()
        val count = (width / pitch).toInt().coerceAtLeast(1)
        val samples = PlaybackVisualizerBuffer.shared.snapshot(count)
        val barWidth = host.dp(3).toFloat()
        val baseline = height / 2f
        val maxHalfHeight = (height - host.dp(6)) / 2f
        val left = (width - (count - 1) * pitch - barWidth) / 2f
        for (index in samples.indices) {
            val halfHeight = host.dp(2) / 2f + sqrt(samples[index]) * (maxHalfHeight - host.dp(2) / 2f)
            paint.color = if (index >= count - 3) host.yellow else host.purple
            paint.alpha = if (samples[index] > 0f) 230 else 65
            val x = left + index * pitch
            canvas.drawRoundRect(x, baseline - halfHeight, x + barWidth, baseline + halfHeight,
                barWidth / 2f, barWidth / 2f, paint)
        }
        if (active) postInvalidateDelayed(50L)
    }

    override fun close() {
        setActive(false)
    }
}
