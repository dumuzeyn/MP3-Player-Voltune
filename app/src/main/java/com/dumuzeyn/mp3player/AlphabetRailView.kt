package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class AlphabetRailView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var entries: List<AlphabetIndex.Entry> = emptyList()
    private var selected = -1
    private var listener: ((Int) -> Unit)? = null

    init {
        contentDescription = "Alphabet fast scroll"
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(dp(3), dp(5), dp(3), dp(5))
    }

    fun configure(values: List<AlphabetIndex.Entry>, foreground: Int, panel: Int, accent: Int,
        onSelect: (Int) -> Unit) {
        entries = values
        listener = onSelect
        backgroundPaint.color = panel
        selectedPaint.color = accent
        textPaint.color = foreground
        visibility = if (values.size > 1) VISIBLE else GONE
        selected = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (entries.isEmpty()) return
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), dp(8f), dp(8f),
            backgroundPaint)
        val available = (height - paddingTop - paddingBottom).toFloat()
        val cell = available / entries.size
        textPaint.textSize = min(dp(11f), max(dp(7f), cell * 0.72f))
        entries.forEachIndexed { index, entry ->
            val centerY = paddingTop + cell * (index + 0.5f)
            if (index == selected) canvas.drawCircle(width / 2f, centerY, min(width * 0.43f, cell * 0.48f),
                selectedPaint)
            val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(entry.label, width / 2f, baseline, textPaint)
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
        if (selected == index) return
        selected = index
        contentDescription = "${entries[index].label}, alphabet fast scroll"
        listener?.invoke(entries[index].position)
        invalidate()
    }

    private fun dp(value: Int): Int = (value * density).toInt().coerceAtLeast(1)
    private fun dp(value: Float): Float = value * density
}
