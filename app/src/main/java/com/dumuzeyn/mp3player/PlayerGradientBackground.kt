package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class PlayerGradientBackground(
    context: Context,
    private val config: Config,
    private val startColor: Int,
    private val endColor: Int,
) : View(context) {
    interface Config {
        fun animationsEnabled(): Boolean
        fun darkTheme(): Boolean
        fun baseColor(): Int
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var uiActive = true

    fun setUiActive(active: Boolean) {
        uiActive = active
        if (active) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val phase = if (uiActive && config.animationsEnabled()) {
            (SystemClock.uptimeMillis() % ANIMATION_DURATION_MS) / ANIMATION_DURATION_MS.toFloat()
        } else {
            STATIC_PHASE
        }
        val angle = phase * Math.PI * 2.0
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val radius = max(width, height) * 0.72f
        val dx = cos(angle).toFloat() * radius
        val dy = sin(angle).toFloat() * radius
        val baseColor = config.baseColor()
        val purpleTint = blend(baseColor, startColor, if (config.darkTheme()) 0.82f else 0.38f)
        val yellowTint = blend(baseColor, endColor, if (config.darkTheme()) 0.72f else 0.30f)
        paint.shader = LinearGradient(
            centerX - dx,
            centerY - dy,
            centerX + dx,
            centerY + dy,
            intArrayOf(baseColor, purpleTint, yellowTint, baseColor),
            floatArrayOf(0f, 0.38f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (uiActive && config.animationsEnabled() && isAttachedToWindow) {
            postInvalidateDelayed(FRAME_DELAY_MS)
        }
    }

    private fun blend(first: Int, second: Int, amount: Float): Int {
        val inverse = 1f - amount
        val red = (((first shr 16) and 255) * inverse + ((second shr 16) and 255) * amount).roundToInt()
        val green = (((first shr 8) and 255) * inverse + ((second shr 8) and 255) * amount).roundToInt()
        val blue = ((first and 255) * inverse + (second and 255) * amount).roundToInt()
        return -0x1000000 or (red shl 16) or (green shl 8) or blue
    }

    private companion object {
        const val ANIMATION_DURATION_MS = 12_000L
        const val FRAME_DELAY_MS = 40L
        const val STATIC_PHASE = 0.18f
    }
}
