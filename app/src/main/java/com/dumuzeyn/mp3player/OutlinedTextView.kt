package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.view.Gravity
import android.widget.TextView
import kotlin.math.max

/** Draws a crisp text stroke without the blur produced by a shadow layer. */
class OutlinedTextView(context: Context) : TextView(context) {
    private var outlineEnabled = false
    private var outlineColor = 0
    private var outlineWidth = 0f

    fun setTextOutline(enabled: Boolean, color: Int, width: Float) {
        outlineEnabled = enabled
        outlineColor = color
        outlineWidth = max(0.5f, width)
        setShadowLayer(0f, 0f, 0f, 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        if (
            outlineEnabled && textLayout != null && !TextOutlinePolicy.isInsideCard(this) &&
            ThemeContrastPolicy.outlineIsDistinct(currentTextColor, outlineColor)
        ) {
            drawOutline(canvas, textLayout)
        }
        super.onDraw(canvas)
    }

    private fun drawOutline(canvas: Canvas, textLayout: Layout) {
        val previousStyle = paint.style
        val previousWidth = paint.strokeWidth
        val previousJoin = paint.strokeJoin
        val previousColor = paint.color

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = outlineWidth
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = outlineColor

        val availableHeight = height - compoundPaddingTop - compoundPaddingBottom
        val verticalOffset = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.BOTTOM -> availableHeight - textLayout.height
            Gravity.CENTER_VERTICAL -> (availableHeight - textLayout.height) / 2
            else -> 0
        }.coerceAtLeast(0)

        val saveCount = canvas.save()
        canvas.translate(
            (compoundPaddingLeft - scrollX).toFloat(),
            (extendedPaddingTop + verticalOffset - scrollY).toFloat(),
        )
        textLayout.draw(canvas)
        canvas.restoreToCount(saveCount)

        paint.style = previousStyle
        paint.strokeWidth = previousWidth
        paint.strokeJoin = previousJoin
        paint.color = previousColor
    }
}
