package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Path
import android.widget.ImageView

/** Clips both static and rotating artwork to the selected cover shape. */
internal open class ShapedCoverImageView(protected val host: MainActivityCore) : ImageView(host) {
    private val clip = Path()
    private var clippedWidth = -1
    private var clippedHeight = -1
    private var clippedShape = ""

    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onDraw(canvas: Canvas) {
        val shape = host.appearanceState.coverShape
        if (clippedWidth != width || clippedHeight != height || clippedShape != shape) {
            rebuildClip(shape)
        }
        val save = canvas.save()
        canvas.clipPath(clip)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }

    fun invalidateCoverShape() {
        clippedShape = ""
        invalidate()
    }

    private fun rebuildClip(shape: String) {
        clippedWidth = width
        clippedHeight = height
        clippedShape = shape
        val w = width.toFloat()
        val h = height.toFloat()
        clip.reset()
        when (shape) {
            "circle" -> clip.addOval(0f, 0f, w, h, Path.Direction.CW)
            "hexagon", "star", "triangle" -> {
                CoverShapeGeometry.vertices(shape, w, h)
                    .forEachIndexed { index, (x, y) ->
                        if (index == 0) clip.moveTo(x, y) else clip.lineTo(x, y)
                    }
                clip.close()
            }
            "diamond" -> {
                clip.moveTo(w * 0.5f, 0f)
                clip.lineTo(w, h * 0.5f)
                clip.lineTo(w * 0.5f, h)
                clip.lineTo(0f, h * 0.5f)
                clip.close()
            }
            else -> clip.addRoundRect(0f, 0f, w, h, host.dp(8).toFloat(),
                host.dp(8).toFloat(), Path.Direction.CW)
        }
    }
}
