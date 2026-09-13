package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

internal class EditorModeBoundsView(private val host: MainActivityCore) : View(host) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = host.dp(3).toFloat()
        val length = host.dp(24).toFloat()
        val right = width - inset
        val bottom = height - inset
        paint.color = host.yellow
        paint.strokeWidth = host.dp(3).toFloat()

        canvas.drawLine(inset, inset, inset + length, inset, paint)
        canvas.drawLine(inset, inset, inset, inset + length, paint)
        canvas.drawLine(right - length, inset, right, inset, paint)
        canvas.drawLine(right, inset, right, inset + length, paint)
        canvas.drawLine(inset, bottom, inset + length, bottom, paint)
        canvas.drawLine(inset, bottom - length, inset, bottom, paint)
        canvas.drawLine(right - length, bottom, right, bottom, paint)
        canvas.drawLine(right, bottom - length, right, bottom, paint)
    }
}
