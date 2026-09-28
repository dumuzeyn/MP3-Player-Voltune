package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** A bounded, normalized line drawing for the custom particle shape. */
internal class ParticleDrawingView(private val host: MainActivityCore) : View(host) {
    private val strokes = ArrayList<ArrayList<Pair<Int, Int>>>()
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = host.purple
        style = Paint.Style.STROKE
        strokeWidth = host.dp(4).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init { background = host.uiFactory.cardBackground() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (strokes.sumOf { it.size } >= 128) return true
                strokes.add(ArrayList())
                addPoint(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                addPoint(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                addPoint(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun addPoint(x: Float, y: Float) {
        val stroke = strokes.lastOrNull() ?: return
        if (strokes.sumOf { it.size } >= 128 || width <= 0 || height <= 0) return
        val point = Pair((x / width * 1000).toInt().coerceIn(0, 1000),
            (y / height * 1000).toInt().coerceIn(0, 1000))
        if (point != stroke.lastOrNull()) stroke.add(point)
        invalidate()
    }

    fun encodedPath(): String = strokes.filter { it.size > 1 }.joinToString("|") { stroke ->
        stroke.joinToString(";") { "${it.first},${it.second}" }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { stroke ->
            path.reset()
            stroke.forEachIndexed { index, (x, y) ->
                val px = width * x / 1000f
                val py = height * y / 1000f
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, paint)
        }
    }
}
