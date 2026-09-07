package com.dumuzeyn.mp3player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class ThemeColorWheelView(
    private val host: MainActivityCore,
    initialColor: Int,
    private val listener: Listener,
) : View(host) {
    fun interface Listener {
        fun onColorPicked(color: Int)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = floatArrayOf(0f, 0f, 1f)
    private var wheelBitmap: Bitmap? = null
    private var wheelSize = 0
    private var wheelRadius = 0
    private var wheelCenterX = 0
    private var wheelCenterY = 0
    private var brightnessTop = 0
    private var brightnessHeight = 0

    init {
        Color.colorToHSV(initialColor, hsv)
        hsv[2] = hsv[2].coerceAtLeast(0.02f)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        brightnessHeight = host.dp(28)
        wheelSize = minOf(width, height - host.dp(52)).coerceAtLeast(1)
        wheelRadius = (wheelSize / 2).coerceAtLeast(1)
        wheelCenterX = width / 2
        wheelCenterY = wheelRadius
        brightnessTop = wheelSize + host.dp(18)
        wheelBitmap = buildWheelBitmap(wheelSize)
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = wheelBitmap ?: return
        canvas.drawBitmap(bitmap, (wheelCenterX - wheelRadius).toFloat(), 0f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = host.dp(2).toFloat()
        paint.color = ThemeManager.mixColor(host.fg, host.bg, 0.65f)
        canvas.drawCircle(
            wheelCenterX.toFloat(),
            wheelCenterY.toFloat(),
            (wheelRadius - host.dp(1)).toFloat(),
            paint,
        )

        val angle = Math.toRadians(hsv[0].toDouble()).toFloat()
        val selectorRadius = hsv[1] * wheelRadius
        val selectorX = wheelCenterX + cos(angle) * selectorRadius
        val selectorY = wheelCenterY + sin(angle) * selectorRadius
        paint.strokeWidth = host.dp(3).toFloat()
        paint.color = ThemeManager.readableOn(Color.HSVToColor(hsv))
        canvas.drawCircle(selectorX, selectorY, host.dp(9).toFloat(), paint)
        drawBrightness(canvas)
    }

    private fun buildWheelBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val radius = (size / 2).coerceAtLeast(1)
        val pixelHsv = floatArrayOf(0f, 0f, 1f)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - radius).toFloat()
                val dy = (y - radius).toFloat()
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > radius) {
                    bitmap.setPixel(x, y, Color.TRANSPARENT)
                    continue
                }
                val hue = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                pixelHsv[0] = if (hue < 0f) hue + 360f else hue
                pixelHsv[1] = (distance / radius).coerceAtMost(1f)
                bitmap.setPixel(x, y, Color.HSVToColor(pixelHsv))
            }
        }
        return bitmap
    }

    private fun drawBrightness(canvas: Canvas) {
        val left = host.dp(2)
        val right = width - host.dp(2)
        for (x in left..right) {
            val value = (x - left).toFloat() / (right - left).coerceAtLeast(1)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], value))
            canvas.drawLine(
                x.toFloat(),
                brightnessTop.toFloat(),
                x.toFloat(),
                (brightnessTop + brightnessHeight).toFloat(),
                paint,
            )
        }
        paint.strokeWidth = host.dp(2).toFloat()
        paint.color = ThemeManager.mixColor(host.fg, host.bg, 0.65f)
        canvas.drawRect(
            left.toFloat(),
            brightnessTop.toFloat(),
            right.toFloat(),
            (brightnessTop + brightnessHeight).toFloat(),
            paint,
        )
        val knobX = left + hsv[2] * (right - left)
        paint.strokeWidth = host.dp(3).toFloat()
        paint.color = ThemeManager.readableOn(Color.HSVToColor(hsv))
        canvas.drawCircle(
            knobX,
            brightnessTop + brightnessHeight / 2f,
            host.dp(8).toFloat(),
            paint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_MOVE
        ) {
            return true
        }
        if (event.y >= brightnessTop - host.dp(8)) {
            val left = host.dp(2)
            val right = width - host.dp(2)
            hsv[2] = ((event.x - left) / (right - left).coerceAtLeast(1)).coerceIn(0f, 1f)
        } else {
            val dx = event.x - wheelCenterX
            val dy = event.y - wheelCenterY
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= wheelRadius) {
                val hue = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                hsv[0] = if (hue < 0f) hue + 360f else hue
                hsv[1] = (distance / wheelRadius).coerceAtMost(1f)
            }
        }
        listener.onColorPicked(Color.HSVToColor(hsv))
        invalidate()
        return true
    }
}
