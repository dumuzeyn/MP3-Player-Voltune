package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToLong

internal class AudioEditorWaveformView(
    private val host: MainActivityCore,
    private val clip: AudioEditClip,
) : View(host) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var subscription: AutoCloseable? = null
    var waveform: AudioWaveform? = null
        private set
    private var failed = false
    private var drag = 0
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(host).scaledTouchSlop
    private var selection = AudioWaveformSelection(clip.sourceDurationMs, clip.startMs, clip.endMs)
    var cursorMs = clip.startMs + clip.durationMs / 2
        private set
    var onSelection: ((Long, Long) -> Unit)? = null
    var onCursor: ((Long) -> Unit)? = null
    private val margin get() = host.dp(16).toFloat()
    private val span get() = (width - margin * 2).coerceAtLeast(1f)

    init { contentDescription = host.tr("Audio waveform selection", "Выделение на звуковой волне") }

    fun setSelection(startMs: Long, endMs: Long) {
        if (startMs < 0 || endMs <= startMs || endMs > clip.sourceDurationMs) return
        selection = AudioWaveformSelection(clip.sourceDurationMs, startMs, endMs)
        invalidate()
    }

    fun setCursor(value: Long) {
        cursorMs = value.coerceIn(clip.startMs, clip.endMs)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscription = host.audioEditorController.waveforms.request(clip) {
            waveform = it.getOrNull()
            failed = it.isFailure
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        subscription?.close()
        subscription = null
        parent?.requestDisallowInterceptTouchEvent(false)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val top = host.dp(12).toFloat()
        val bottom = height - host.dp(24).toFloat()
        val center = (top + bottom) / 2
        val left = xAt(selection.startMs)
        val right = xAt(selection.endMs)
        paint.color = host.purple
        paint.alpha = 28
        canvas.drawRect(left, top, right, bottom, paint)
        paint.alpha = 255
        val data = waveform
        if (data != null) {
            paint.strokeWidth = host.dp(1).toFloat().coerceAtLeast(1f)
            var x = margin
            val step = host.dp(2).toFloat().coerceAtLeast(2f)
            while (x < width - margin) {
                val from = timeAt(x) * 1000
                val to = timeAt(x + step) * 1000
                val amplitude = data.peakBetween(from, to) * (bottom - top) * 0.46f
                paint.color = if (x in left..right) host.purple else host.secondaryText
                canvas.drawLine(x, center - amplitude, x, center + amplitude, paint)
                x += step
            }
        } else {
            paint.color = host.secondaryText
            paint.textSize = host.dp(12).toFloat()
            val label = if (failed) host.tr("Waveform unavailable", "Волна недоступна")
                else host.tr("Loading waveform", "Загрузка волны")
            canvas.drawText(label, (width - paint.measureText(label)) / 2, center, paint)
        }
        paint.color = host.yellowDark
        paint.strokeWidth = host.dp(2).toFloat()
        canvas.drawLine(xAt(cursorMs), top, xAt(cursorMs), bottom, paint)
        paint.color = host.purple
        for (x in floatArrayOf(left, right)) {
            canvas.drawLine(x, top, x, bottom, paint)
            canvas.drawRoundRect(x - host.dp(4), center - host.dp(12), x + host.dp(4),
                center + host.dp(12), host.dp(3).toFloat(), host.dp(3).toFloat(), paint)
        }
        paint.color = host.secondaryText
        paint.textSize = host.dp(11).toFloat()
        canvas.drawText("0:00", margin, height - host.dp(5).toFloat(), paint)
        val end = host.formatSeconds(clip.sourceDurationMs / 1000)
        canvas.drawText(end, width - margin - paint.measureText(end), height - host.dp(5).toFloat(), paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val startDistance = abs(event.x - xAt(selection.startMs))
                val endDistance = abs(event.x - xAt(selection.endMs))
                drag = if (minOf(startDistance, endDistance) <= host.dp(24)) {
                    if (startDistance <= endDistance) 1 else 2
                } else 3
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (drag != 0) {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dy > touchSlop && dy > dx) {
                    drag = 0
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return false
                }
                if (dx > touchSlop) move(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && drag != 0) { move(event.x); performClick() }
                drag = 0
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun move(x: Float) {
        val time = timeAt(x)
        when (drag) {
            1 -> selection.start(time)
            2 -> selection.end(time)
            else -> { setCursor(time); onCursor?.invoke(cursorMs) }
        }
        if (drag == 1 || drag == 2) onSelection?.invoke(selection.startMs, selection.endMs)
        invalidate()
    }

    private fun timeAt(x: Float) = (((x - margin) / span).coerceIn(0f, 1f) *
        clip.sourceDurationMs.toDouble()).roundToLong()
    private fun xAt(timeMs: Long) = margin + span * timeMs / clip.sourceDurationMs
    override fun performClick(): Boolean { super.performClick(); return true }
}
