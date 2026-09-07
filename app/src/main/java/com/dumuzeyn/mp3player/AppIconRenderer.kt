package com.dumuzeyn.mp3player

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

/** Central renderer for every runtime Voltune icon used by the application UI. */
internal object AppIconRenderer {
    private val brandBackground = 0xff090218.toInt()

    @JvmStatic
    fun renderLogo(
        context: Context,
        primaryColor: Int,
        secondaryColor: Int,
        size: Int,
    ): Bitmap {
        val safeSize = size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val source = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.voltune_icon_foreground,
        )
        val inset = (safeSize * 0.08f).roundToInt()
        val contentSize = (safeSize - inset * 2).coerceAtLeast(1)
        val scaledSource = Bitmap.createScaledBitmap(source, contentSize, contentSize, true)
        val scaled = scaledSource.copy(Bitmap.Config.ARGB_8888, true)
        if (scaledSource !== source) {
            scaledSource.recycle()
        }
        source.recycle()
        tintLogo(scaled, primaryColor, secondaryColor)
        Canvas(bitmap).drawBitmap(scaled, inset.toFloat(), inset.toFloat(), null)
        scaled.recycle()
        return bitmap
    }

    @JvmStatic
    fun renderTile(
        context: Context,
        backgroundColor: Int,
        primaryColor: Int,
        secondaryColor: Int,
        size: Int,
    ): Bitmap {
        val safeSize = size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (Color.alpha(backgroundColor) == 0) brandBackground else backgroundColor
        }
        val radius = safeSize * 0.22f
        canvas.drawRoundRect(
            0f,
            0f,
            safeSize.toFloat(),
            safeSize.toFloat(),
            radius,
            radius,
            background,
        )

        val inset = (safeSize * 0.10f).roundToInt()
        val logo = renderLogo(context, primaryColor, secondaryColor, safeSize - inset * 2)
        canvas.drawBitmap(logo, inset.toFloat(), inset.toFloat(), null)
        logo.recycle()
        return bitmap
    }

    @JvmStatic
    fun renderPreview(
        context: Context,
        backgroundColor: Int,
        primaryColor: Int,
        secondaryColor: Int,
        size: Int,
    ): Bitmap = try {
        renderTile(context, backgroundColor, primaryColor, secondaryColor, size)
    } catch (_: RuntimeException) {
        BitmapFactory.decodeResource(context.resources, context.applicationInfo.icon)
    }

    @JvmStatic
    fun renderLauncherPreview(
        context: Context,
        component: ComponentName,
        fallbackBackground: Int,
        primaryColor: Int,
        secondaryColor: Int,
        size: Int,
    ): Bitmap {
        val safeSize = size.coerceAtLeast(1)
        return try {
            val icon = context.packageManager.getActivityIcon(component)
            val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, safeSize, safeSize)
            icon.draw(Canvas(bitmap))
            bitmap
        } catch (_: PackageManager.NameNotFoundException) {
            renderPreview(context, fallbackBackground, primaryColor, secondaryColor, safeSize)
        } catch (_: RuntimeException) {
            renderPreview(context, fallbackBackground, primaryColor, secondaryColor, safeSize)
        }
    }

    private fun tintLogo(bitmap: Bitmap, primaryColor: Int, secondaryColor: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val source = pixels[index]
                val alpha = Color.alpha(source)
                if (alpha == 0) continue

                val gradient = if (width == 1) 0f else x.toFloat() / (width - 1)
                val themed = blend(primaryColor, secondaryColor, gradient)
                val luminance = (
                    Color.red(source) * 0.2126f +
                        Color.green(source) * 0.7152f +
                        Color.blue(source) * 0.0722f
                    ) / 255f
                val shade = 0.72f + luminance * 0.48f
                pixels[index] = Color.argb(
                    alpha,
                    (Color.red(themed) * shade).roundToInt().coerceIn(0, 255),
                    (Color.green(themed) * shade).roundToInt().coerceIn(0, 255),
                    (Color.blue(themed) * shade).roundToInt().coerceIn(0, 255),
                )
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun blend(first: Int, second: Int, amount: Float): Int {
        val inverse = 1f - amount
        return Color.rgb(
            (Color.red(first) * inverse + Color.red(second) * amount).roundToInt(),
            (Color.green(first) * inverse + Color.green(second) * amount).roundToInt(),
            (Color.blue(first) * inverse + Color.blue(second) * amount).roundToInt(),
        )
    }
}
