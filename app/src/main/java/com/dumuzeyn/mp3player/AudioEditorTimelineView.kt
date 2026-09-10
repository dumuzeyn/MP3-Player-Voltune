package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View

internal class AudioEditorTimelineView(
    private val host: MainActivityCore,
    private val project: AudioEditProject,
    private val select: (AudioEditClip) -> Unit,
) : View(host) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = LinkedHashMap<AudioEditClip, RectF>()
    private var touched: AudioEditClip? = null
    private val lanes = project.clips.map { it.lane }.distinct().sorted()
    private val subscriptions = ArrayList<AutoCloseable>()
    private val waves = HashMap<String, AudioWaveform>()

    init {
        contentDescription = host.tr("Audio timeline", "Монтажная шкала")
        minimumHeight = host.dp(40 + lanes.size.coerceAtLeast(1) * 64)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        project.clips.distinctBy { it.uri }.forEach { clip ->
            subscriptions.add(host.audioEditorController.waveforms.request(clip) { result ->
                result.getOrNull()?.let { waves[clip.uri] = it }
                invalidate()
            })
        }
    }

    override fun onDetachedFromWindow() {
        subscriptions.forEach { it.close() }
        subscriptions.clear()
        waves.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        bounds.clear()
        paint.textSize = host.dp(11).toFloat()
        paint.color = host.secondaryText
        canvas.drawText("0:00", 0f, host.dp(16).toFloat(), paint)
        val duration = host.formatSeconds(project.durationMs / 1000)
        canvas.drawText(duration, width - paint.measureText(duration), host.dp(16).toFloat(), paint)
        val total = project.durationMs.coerceAtLeast(1).toFloat()
        lanes.forEachIndexed { index, lane ->
            val top = host.dp(28 + index * 64).toFloat()
            paint.color = host.line
            canvas.drawLine(0f, top + host.dp(56), width.toFloat(), top + host.dp(56), paint)
            project.clips.filter { it.lane == lane }.forEach { clip ->
                val rect = RectF(width * clip.offsetMs / total, top,
                    width * clip.finishMs / total, top + host.dp(52))
                bounds[clip] = rect
                paint.color = if (index % 2 == 0) host.purple else host.yellowDark
                canvas.drawRoundRect(rect, host.dp(4).toFloat(), host.dp(4).toFloat(), paint)
                canvas.save()
                canvas.clipRect(rect)
                paint.color = android.graphics.Color.WHITE
                val label = TextUtils.ellipsize(clip.title, paint,
                    (rect.width() - host.dp(10)).coerceAtLeast(0f), TextUtils.TruncateAt.END)
                canvas.drawText(label.toString(), rect.left + host.dp(5), top + host.dp(16), paint)
                waves[clip.uri]?.let { wave ->
                    val step = host.dp(2).toFloat().coerceAtLeast(2f)
                    paint.strokeWidth = host.dp(1).toFloat()
                    var x = rect.left
                    while (x < rect.right && rect.width() > 0) {
                        val start = clip.startMs + ((x - rect.left) / rect.width() * clip.durationMs).toLong()
                        val end = clip.startMs + ((x + step - rect.left) / rect.width() * clip.durationMs).toLong()
                        val amplitude = wave.peakBetween(start * 1000, minOf(end, clip.endMs) * 1000) *
                            clip.gain * host.dp(14)
                        canvas.drawLine(x, top + host.dp(35) - amplitude, x, top + host.dp(35) + amplitude, paint)
                        x += step
                    }
                }
                canvas.restore()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touched = bounds.entries.firstOrNull { it.value.contains(event.x, event.y) }?.key
                return touched != null
            }
            MotionEvent.ACTION_UP -> {
                touched?.let(select)
                touched = null
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> touched = null
        }
        return touched != null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
