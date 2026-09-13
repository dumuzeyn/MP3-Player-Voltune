package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class AlphabetRailView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val selectedTextPaint = Paint(textPaint)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientStart = context.getColor(R.color.voltune_scrollbar_start)
    private val gradientEnd = context.getColor(R.color.voltune_scrollbar_end)
    private var entries: List<AlphabetIndex.Entry> = emptyList()
    private var trackCount = 0
    private var selected = -1
    private var scrollProgress = 0f
    private var dragging = false
    private var listener: ((Float) -> Unit)? = null

    init {
        contentDescription = "Alphabet fast scroll"
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(dp(3), dp(5), dp(3), dp(5))
    }

    fun configure(
        values: List<AlphabetIndex.Entry>,
        totalTracks: Int,
        foreground: Int,
        onScroll: (Float) -> Unit,
    ) {
        entries = values
        trackCount = totalTracks
        listener = onScroll
        selectedPaint.color = gradientEnd
        textPaint.color = foreground
        selectedTextPaint.color = android.graphics.Color.BLACK
        visibility = if (values.size > 1) VISIBLE else GONE
        selected = -1
        scrollProgress = 0f
        if (values.isNotEmpty()) selectTrackPosition(0)
        invalidate()
    }

    fun syncToList(progress: Float, trackPosition: Int) {
        if (entries.isEmpty() || dragging) return
        scrollProgress = progress.coerceIn(0f, 1f)
        selectTrackPosition(trackPosition)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (entries.isEmpty()) return
        val available = (height - paddingTop - paddingBottom).toFloat()
        val cell = available / entries.size
        val thumbHeight = min(available, min(dp(42f), max(dp(24f), cell * 0.9f)))
        val thumbTravel = max(0f, available - thumbHeight)
        val thumbTop = paddingTop + thumbTravel * scrollProgress
        val thumbBottom = thumbTop + thumbHeight
        val halfThumb = dp(1.5f)
        val thumbCenterX = width - dp(5f)
        thumbPaint.shader = LinearGradient(
            0f, thumbTop, 0f, thumbBottom, gradientStart, gradientEnd, Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(thumbCenterX - halfThumb, thumbTop, thumbCenterX + halfThumb, thumbBottom),
            halfThumb, halfThumb, thumbPaint,
        )
        val labelCenterX = width - dp(12f)
        textPaint.textSize = min(dp(11f), max(dp(7f), cell * 0.72f))
        entries.forEachIndexed { index, entry ->
            val labelCenterY = paddingTop + cell * (index + 0.5f)
            if (index == selected) {
                val radius = min(dp(6f), max(dp(5f), cell * 0.46f))
                canvas.drawCircle(labelCenterX, labelCenterY, radius, selectedPaint)
            }
            selectedTextPaint.textSize = textPaint.textSize
            val paint = if (index == selected) selectedTextPaint else textPaint
            val baseline = labelCenterY - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(entry.label, labelCenterX, baseline, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (entries.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                scrollTo(event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                scrollTo(event.y)
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun scrollTo(y: Float) {
        val available = (height - paddingTop - paddingBottom).coerceAtLeast(1).toFloat()
        val cell = available / entries.size
        val thumbHeight = min(available, min(dp(42f), max(dp(24f), cell * 0.9f)))
        val travel = max(1f, available - thumbHeight)
        scrollProgress = ((y - paddingTop - thumbHeight / 2f) / travel).coerceIn(0f, 1f)
        val position = (scrollProgress * (trackCount - 1).coerceAtLeast(0)).roundToInt()
        selectTrackPosition(position)
        listener?.invoke(scrollProgress)
        invalidate()
    }

    private fun selectTrackPosition(position: Int) {
        var index = 0
        for (candidate in entries.indices) {
            if (entries[candidate].position > position) break
            index = candidate
        }
        if (selected == index) return
        selected = index
        contentDescription = "${entries[index].label}, alphabet fast scroll"
    }

    private fun dp(value: Int): Int = (value * density).toInt().coerceAtLeast(1)
    private fun dp(value: Float): Float = value * density
}
