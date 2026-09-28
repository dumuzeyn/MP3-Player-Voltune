package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Path
import android.widget.ImageView

/** Clips both static and rotating artwork to the selected cover shape. */
internal open class ShapedCoverImageView(protected val host: MainActivityCore) : ImageView(host) {
    private val clip = Path()

    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        val w = width.toFloat()
        val h = height.toFloat()
        clip.reset()
        when (host.appearanceState.coverShape) {
            "circle" -> clip.addOval(0f, 0f, w, h, Path.Direction.CW)
            "hexagon" -> {
                clip.moveTo(w * 0.25f, 0f)
                clip.lineTo(w * 0.75f, 0f)
                clip.lineTo(w, h * 0.5f)
                clip.lineTo(w * 0.75f, h)
                clip.lineTo(w * 0.25f, h)
                clip.lineTo(0f, h * 0.5f)
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
        canvas.clipPath(clip)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }
}
