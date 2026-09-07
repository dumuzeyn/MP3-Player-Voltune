package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Trace
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class WaveformView(
    context: Context,
    key: String,
    private var color: Int,
    private var accentColor: Int,
    private var waveformActive: Boolean,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@WaveformView.color
        strokeCap = Paint.Cap.ROUND
    }
    private var seed = abs(key.hashCode())
    private var transitionPausedState = false
    private var frameScheduled = false
    private var progress = 0f
    private var startedAt = System.currentTimeMillis()
    private val nextFrame = Runnable {
        frameScheduled = false
        if (shouldAnimate()) invalidate()
    }

    fun setActive(active: Boolean) {
        if (waveformActive == active) return
        waveformActive = active
        startedAt = System.currentTimeMillis()
        updateAnimationState()
    }

    fun setTrackKey(key: String?) {
        val nextSeed = abs(key.orEmpty().hashCode())
        if (seed != nextSeed) {
            seed = nextSeed
            startedAt = System.currentTimeMillis()
            progress = 0f
            invalidate()
        }
    }

    fun setProgress(positionMs: Long, durationMs: Long) {
        val next = if (durationMs <= 0L) {
            0f
        } else {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
        if (abs(progress - next) >= 0.002f) {
            progress = next
            if (waveformActive) invalidate()
        }
    }

    fun setState(color: Int, accentColor: Int, active: Boolean) {
        val changed = this.color != color || this.accentColor != accentColor ||
            waveformActive != active
        if (!changed) return
        this.color = color
        this.accentColor = accentColor
        if (waveformActive != active) startedAt = System.currentTimeMillis()
        waveformActive = active
        updateAnimationState()
    }

    fun setTransitionPaused(paused: Boolean) {
        if (transitionPausedState == paused) return
        transitionPausedState = paused
        updateAnimationState()
    }

    override fun onDraw(canvas: Canvas) {
        if (waveformActive) Trace.beginSection("Voltune/Home.activeWaveformDraw")
        try {
            super.onDraw(canvas)
            val width = width
            val height = height
            if (width <= 0 || height <= 0) return

            val bars = 24
            val gap = width / (bars * 1.55f)
            paint.strokeWidth = (gap * 0.34f).coerceAtLeast(3f)
            val time = (System.currentTimeMillis() - startedAt) /
                if (waveformActive) 180f else 520f

            for (index in 0 until bars) {
                val played = waveformActive && index <= (progress * (bars - 1)).roundToInt()
                paint.color = if (played) accentColor else color
                val x = gap + index * gap * 1.48f
                val base = 0.24f + ((seed shr (index % 12)) and 15) / 22f
                val pulse = if (waveformActive) sin(time + index * 0.7f) * 0.22f else 0f
                val bar = (base + pulse).coerceIn(0.18f, 0.92f)
                val center = height * 0.5f
                val half = height * bar * 0.38f
                canvas.drawLine(x, center - half, x, center + half, paint)
            }
            scheduleNextFrame()
        } finally {
            if (waveformActive) Trace.endSection()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        cancelNextFrame()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimationState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimationState()
    }

    private fun shouldAnimate(): Boolean =
        waveformActive && !transitionPausedState && isAttachedToWindow &&
            windowVisibility == VISIBLE && isShown

    private fun updateAnimationState() {
        if (!shouldAnimate()) {
            cancelNextFrame()
            invalidate()
            return
        }
        invalidate()
        scheduleNextFrame()
    }

    private fun scheduleNextFrame() {
        if (!shouldAnimate() || frameScheduled) return
        frameScheduled = true
        postDelayed(nextFrame, 48L)
    }

    private fun cancelNextFrame() {
        if (!frameScheduled) return
        removeCallbacks(nextFrame)
        frameScheduled = false
    }
}
