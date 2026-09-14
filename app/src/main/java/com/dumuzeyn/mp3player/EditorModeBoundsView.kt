package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

internal class EditorModeBoundsView(private val host: MainActivityCore) : View(host) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = host.dp(1).toFloat()
        val horizontalLength = host.dp(20).toFloat()
        val verticalLength = host.dp(14).toFloat()
        val right = width - inset
        val bottom = height - inset
        paint.color = host.yellow
        paint.strokeWidth = host.dp(2).toFloat()

        canvas.drawLine(inset, inset, inset + horizontalLength, inset, paint)
        canvas.drawLine(inset, inset, inset, inset + verticalLength, paint)
        canvas.drawLine(right - horizontalLength, inset, right, inset, paint)
        canvas.drawLine(right, inset, right, inset + verticalLength, paint)
        canvas.drawLine(inset, bottom, inset + horizontalLength, bottom, paint)
        canvas.drawLine(inset, bottom - verticalLength, inset, bottom, paint)
        canvas.drawLine(right - horizontalLength, bottom, right, bottom, paint)
        canvas.drawLine(right, bottom - verticalLength, right, bottom, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
