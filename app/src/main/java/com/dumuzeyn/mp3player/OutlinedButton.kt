package com.dumuzeyn.mp3player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.widget.Button
import kotlin.math.max

/** Button counterpart of OutlinedTextView for a consistent crisp text stroke. */
class OutlinedButton(context: Context) : Button(context) {
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
        if (
            !outlineEnabled || TextOutlinePolicy.isInsideCard(this) ||
            !ThemeContrastPolicy.outlineIsDistinct(currentTextColor, outlineColor)
        ) {
            super.onDraw(canvas)
            return
        }
        drawOutlined(canvas)
    }

    @SuppressLint("WrongCall")
    private fun drawOutlined(canvas: Canvas) {
        val originalColors = textColors
        val offset = max(0.5f, outlineWidth * 0.55f)
        setTextColor(outlineColor)
        val directions = floatArrayOf(-offset, 0f, offset)
        for (dx in directions) {
            for (dy in directions) {
                if (dx == 0f && dy == 0f) continue
                val saveCount = canvas.save()
                canvas.translate(dx, dy)
                super.onDraw(canvas)
                canvas.restoreToCount(saveCount)
            }
        }
        setTextColor(originalColors)
        super.onDraw(canvas)
    }
}
