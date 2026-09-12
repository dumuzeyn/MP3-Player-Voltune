package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
    private var selected = -1
    private var listener: ((Int) -> Unit)? = null

    init {
        contentDescription = "Alphabet fast scroll"
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(dp(3), dp(5), dp(3), dp(5))
    }

    fun configure(values: List<AlphabetIndex.Entry>, foreground: Int, onSelect: (Int) -> Unit) {
        entries = values
        listener = onSelect
        selectedPaint.color = gradientEnd
        textPaint.color = foreground
        selectedTextPaint.color = android.graphics.Color.BLACK
        visibility = if (values.size > 1) VISIBLE else GONE
        selected = -1
        if (values.isNotEmpty()) selectIndex(0, false)
        invalidate()
    }

    fun syncToTrackPosition(position: Int) {
        if (entries.isEmpty()) return
        var index = 0
        for (candidate in entries.indices) {
            if (entries[candidate].position > position) break
            index = candidate
        }
        selectIndex(index, false)
    }

    override fun onDraw(canvas: Canvas) {
        if (entries.isEmpty()) return
        val available = (height - paddingTop - paddingBottom).toFloat()
        val cell = available / entries.size
        val centerY = paddingTop + cell * (selected.coerceAtLeast(0) + 0.5f)
        val thumbHeight = min(available, min(dp(42f), max(dp(24f), cell * 0.9f)))
        val thumbTop = (centerY - thumbHeight / 2f).coerceIn(
            paddingTop.toFloat(), height - paddingBottom - thumbHeight,
        )
        val thumbBottom = thumbTop + thumbHeight
        val halfThumb = dp(1.5f)
        val thumbCenterX = width - dp(2.5f)
        thumbPaint.shader = LinearGradient(
            0f, thumbTop, 0f, thumbBottom, gradientStart, gradientEnd, Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(thumbCenterX - halfThumb, thumbTop, thumbCenterX + halfThumb, thumbBottom),
            halfThumb, halfThumb, thumbPaint,
        )
        val labelCenterX = (width - dp(8f)) / 2f
        textPaint.textSize = min(dp(11f), max(dp(7f), cell * 0.72f))
        entries.forEachIndexed { index, entry ->
            val labelCenterY = paddingTop + cell * (index + 0.5f)
            if (index == selected) canvas.drawCircle(
                labelCenterX, labelCenterY,
                min(dp(7f), max(dp(5f), cell * 0.46f)), selectedPaint,
            )
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
                parent?.requestDisallowInterceptTouchEvent(true)
                select(event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                select(event.y)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun select(y: Float) {
        val available = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val index = floor((y - paddingTop) / available * entries.size).toInt()
            .coerceIn(0, entries.lastIndex)
        selectIndex(index, true)
    }

    private fun selectIndex(index: Int, notify: Boolean) {
        if (selected == index) return
        selected = index
        contentDescription = "${entries[index].label}, alphabet fast scroll"
        if (notify) listener?.invoke(entries[index].position)
        invalidate()
    }

    private fun dp(value: Int): Int = (value * density).toInt().coerceAtLeast(1)
    private fun dp(value: Float): Float = value * density
}
