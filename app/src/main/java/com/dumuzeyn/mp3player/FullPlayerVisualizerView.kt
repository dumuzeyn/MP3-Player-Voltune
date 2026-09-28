package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.pow

/** Draws live playback levels only while the full player is visible. */
internal class FullPlayerVisualizerView(
    private val host: MainActivityCore,
    private val isPlaying: () -> Boolean,
) : View(host), AutoCloseable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = false
    private var displayed = FloatArray(0)

    init {
        contentDescription = host.tr("Audio visualizer", "Аудиовизуализатор")
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        PlaybackVisualizerBuffer.shared.setEnabled(value)
        if (!value) displayed = FloatArray(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pitch = host.dp(6).toFloat()
        val count = (width / pitch).toInt().coerceAtLeast(1)
        val samples = if (active && isPlaying()) {
            PlaybackVisualizerBuffer.shared.snapshot(count)
        } else {
            FloatArray(count)
        }
        if (displayed.size != count) displayed = FloatArray(count)
        val quietest = samples.minOrNull() ?: 0f
        val spread = ((samples.maxOrNull() ?: 0f) - quietest).coerceAtLeast(0.01f)
        val loudness = (samples.average().toFloat() * 3f).coerceIn(0f, 1f).pow(0.55f)
        val barWidth = host.dp(3).toFloat()
        val baseline = height / 2f
        val maxHalfHeight = (height - host.dp(6)) / 2f
        val left = (width - (count - 1) * pitch - barWidth) / 2f
        for (index in samples.indices) {
            val contrast = ((samples[index] - quietest) / spread).coerceIn(0f, 1f)
            val target = if (samples[index] < 0.002f) 0f else
                (0.28f + 0.24f * loudness + 0.48f * contrast).coerceAtMost(1f)
            val response = if (target > displayed[index]) 0.8f else 0.6f
            displayed[index] += (target - displayed[index]) * response
            val halfHeight = host.dp(2) / 2f + displayed[index] * (maxHalfHeight - host.dp(2) / 2f)
            paint.color = if (index in count / 2 - 1..count / 2 + 1) host.yellow else host.purple
            paint.alpha = if (displayed[index] > 0.02f) 230 else 65
            val x = left + index * pitch
            canvas.drawRoundRect(x, baseline - halfHeight, x + barWidth, baseline + halfHeight,
                barWidth / 2f, barWidth / 2f, paint)
        }
        if (active) postInvalidateDelayed(33L)
    }

    override fun close() {
        setActive(false)
    }
}
