package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import kotlin.math.abs

internal class AudioEditorTimelineView(
    private val host: MainActivityCore,
    private val project: AudioEditProject,
    private val selectedClipId: String?,
    private val select: (AudioEditClip) -> Unit,
    private val moveClip: (AudioEditClip, Int, Long) -> Boolean,
) : View(host) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = LinkedHashMap<AudioEditClip, RectF>()
    private var touched: AudioEditClip? = null
    private var dragging: AudioEditClip? = null
    private var dragLane = 0
    private var dragOffsetMs = 0L
    private var dragValid = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(host).scaledTouchSlop
    private val beginDrag = Runnable {
        val clip = touched ?: return@Runnable
        dragging = clip
        dragLane = clip.lane
        dragOffsetMs = clip.offsetMs
        dragValid = true
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }
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
        cancelGesture()
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
            project.clips.filter { it.lane == lane || it == dragging && dragLane == lane }.distinct().forEach { clip ->
                if (clip == dragging && clip.lane == lane && dragLane != lane) return@forEach
                val shownOffset = if (clip == dragging) dragOffsetMs else clip.offsetMs
                val rect = RectF(width * shownOffset / total, top,
                    width * (shownOffset + clip.durationMs) / total, top + host.dp(52))
                bounds[clip] = rect
                paint.style = Paint.Style.FILL
                paint.color = if (index % 2 == 0) host.purple else host.yellowDark
                paint.alpha = if (clip == dragging && !dragValid) 110 else 255
                canvas.drawRoundRect(rect, host.dp(4).toFloat(), host.dp(4).toFloat(), paint)
                paint.alpha = 255
                if (clip.id == selectedClipId) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = host.dp(3).toFloat()
                    paint.color = host.yellow
                    val outline = RectF(rect).apply {
                        inset(host.dp(2).toFloat(), host.dp(2).toFloat())
                    }
                    canvas.drawRoundRect(outline, host.dp(4).toFloat(), host.dp(4).toFloat(), paint)
                    paint.style = Paint.Style.FILL
                }
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
                downX = event.x
                downY = event.y
                if (touched != null) postDelayed(beginDrag, LONG_PRESS_MS)
                return touched != null
            }
            MotionEvent.ACTION_MOVE -> {
                val clip = touched ?: return false
                if (dragging == null) {
                    if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                        removeCallbacks(beginDrag)
                        touched = null
                        return false
                    }
                    return true
                }
                val inside = event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                val laneIndex = ((event.y - host.dp(28)) / host.dp(64)).toInt()
                dragValid = inside && laneIndex in lanes.indices
                if (dragValid) {
                    dragLane = lanes[laneIndex]
                    val total = project.durationMs.coerceAtLeast(clip.durationMs)
                    val near = (event.x / width.coerceAtLeast(1) * total - clip.durationMs / 2)
                        .toLong().coerceAtLeast(0)
                    dragOffsetMs = runCatching {
                        project.nearestFreeOffset(clip.id, dragLane, near)
                    }.getOrElse { dragValid = false; clip.offsetMs }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(beginDrag)
                val clip = touched
                val moved = dragging != null
                if (moved && clip != null && dragValid && event.x in 0f..width.toFloat() &&
                    event.y in 0f..height.toFloat()) moveClip(clip, dragLane, dragOffsetMs)
                else if (!moved) clip?.let(select)
                cancelGesture()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return touched != null
    }

    private fun cancelGesture() {
        removeCallbacks(beginDrag)
        parent?.requestDisallowInterceptTouchEvent(false)
        touched = null
        dragging = null
        dragValid = false
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object { private const val LONG_PRESS_MS = 650L }
}
