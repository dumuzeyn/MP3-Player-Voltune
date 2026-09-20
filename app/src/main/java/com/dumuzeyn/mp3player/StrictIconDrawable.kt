package com.dumuzeyn.mp3player

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.widget.Button
import android.view.Gravity
import kotlin.math.min

/** One density-independent line-icon language for every compact action button. */
internal enum class StrictIcon {
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    STOP,
    SHUFFLE,
    REPEAT,
    REPEAT_ONE,
    REPEAT_LIST,
    TIMER,
    SPEED,
    EQUALIZER,
    LEVEL,
    ADD,
    FOLDER,
    PLAYLIST_ADD,
    SEARCH,
    HEART,
    HEART_OUTLINE,
    LIST,
    LIST_ADD,
    CLEAR_LIST,
    REMOVE,
    EDIT,
    CHECK,
    BACK,
    CLOSE,
    DRAG,
    UNDO,
    REDO,
    LOCK,
    VOLUME,
    MUTE,
    CUT,
    SPLIT,
    AUDIO_PROCESS,
    DELETE,
    MUSIC,
    ;

    companion object {
        fun fromLegacy(symbol: String): StrictIcon? = when (symbol.trim()) {
            "▶" -> PLAY
            "Ⅱ" -> PAUSE
            "⏮" -> PREVIOUS
            "⏭" -> NEXT
            "■" -> STOP
            "⇄" -> SHUFFLE
            "↻" -> REPEAT
            "+" -> ADD
            "▣" -> FOLDER
            "⌕" -> SEARCH
            "♥︎", "♥" -> HEART
            "♡︎", "♡" -> HEART_OUTLINE
            "−", "-" -> REMOVE
            "✎" -> EDIT
            "✔" -> CHECK
            "←" -> BACK
            "×" -> CLOSE
            "≡" -> DRAG
            "↶" -> UNDO
            "↷" -> REDO
            "⌖" -> LOCK
            "⌫" -> CLEAR_LIST
            "♪" -> MUSIC
            else -> null
        }
    }
}

internal object StrictIconButtonStyler {
    fun apply(button: Button, icon: StrictIcon) {
        button.text = null
        button.gravity = Gravity.CENTER
        button.includeFontPadding = false
        button.setTag(R.id.strict_button_icon, icon)
        button.setTag(R.id.strict_button_icon_above, false)
        button.compoundDrawablePadding = 0
        button.setPadding(0, 0, 0, 0)
        refreshTint(button)
    }

    fun applyLabeled(button: Button, icon: StrictIcon, label: String, above: Boolean = false) {
        button.text = label
        button.includeFontPadding = false
        button.setTag(R.id.strict_button_icon, icon)
        button.setTag(R.id.strict_button_icon_above, above)
        val density = button.resources.displayMetrics.density
        button.compoundDrawablePadding = (density * if (above) 0f else 6f).toInt()
        if (above) button.setPadding(0, (density * 4f).toInt(), 0, 0)
        refreshTint(button)
    }

    fun refreshTint(button: Button) {
        val icon = button.getTag(R.id.strict_button_icon) as? StrictIcon ?: return
        val above = button.getTag(R.id.strict_button_icon_above) == true
        button.compoundDrawableTintList = null
        val drawable = StrictIconDrawable(button.resources.displayMetrics.density, icon, button.textColors)
        if (!above && button.text.isNullOrEmpty()) {
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            button.background = CenteredStrictIconDrawable(drawable)
            return
        }
        if (above) {
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, drawable, null, null)
        } else {
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
        }
    }

    fun applyOnBackground(button: Button, icon: StrictIcon, background: Drawable) {
        button.text = null
        button.gravity = Gravity.CENTER
        button.includeFontPadding = false
        button.setTag(R.id.strict_button_icon, icon)
        button.setTag(R.id.strict_button_icon_above, false)
        button.compoundDrawablePadding = 0
        button.setPadding(0, 0, 0, 0)
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        val iconDrawable = StrictIconDrawable(
            button.resources.displayMetrics.density,
            icon,
            button.textColors,
        )
        button.background = LayerDrawable(
            arrayOf(background, CenteredStrictIconDrawable(iconDrawable)),
        )
    }
}

/** Draws the icon from normalized 24 x 24 vector geometry at any density. */
internal class StrictIconDrawable(
    private val density: Float,
    private val icon: StrictIcon,
    private val colors: ColorStateList,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = density * 2f
    }
    private val path = Path()
    private var alphaValue = 255

    init {
        paint.color = colors.defaultColor
    }

    override fun draw(canvas: Canvas) {
        val size = min(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return
        val scale = size / 24f
        val save = canvas.save()
        canvas.translate(
            bounds.left + (bounds.width() - size) * 0.5f,
            bounds.top + (bounds.height() - size) * 0.5f,
        )
        canvas.scale(scale, scale)
        paint.strokeWidth = 2f
        paint.alpha = alphaValue
        path.reset()
        drawIcon(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawIcon(canvas: Canvas) {
        when (icon) {
            StrictIcon.PLAY -> polygon(canvas, 8f, 5f, 19f, 12f, 8f, 19f)
            StrictIcon.PAUSE -> lines(canvas, 8f, 5f, 8f, 19f, 16f, 5f, 16f, 19f)
            StrictIcon.PREVIOUS -> {
                line(canvas, 6f, 6f, 6f, 18f)
                polygon(canvas, 18f, 6f, 9f, 12f, 18f, 18f)
            }
            StrictIcon.NEXT -> {
                line(canvas, 18f, 6f, 18f, 18f)
                polygon(canvas, 6f, 6f, 15f, 12f, 6f, 18f)
            }
            StrictIcon.STOP -> rect(canvas, 6f, 6f, 18f, 18f)
            StrictIcon.SHUFFLE -> {
                curve(canvas, 4f, 7f, 10f, 7f, 11f, 17f, 17f, 17f)
                curve(canvas, 4f, 17f, 10f, 17f, 11f, 7f, 17f, 7f)
                chevronRight(canvas, 17f, 7f)
                chevronRight(canvas, 17f, 17f)
            }
            StrictIcon.REPEAT -> repeatBase(canvas)
            StrictIcon.REPEAT_ONE -> {
                repeatBase(canvas)
                paint.strokeWidth = 1.4f
                line(canvas, 10.5f, 10f, 12f, 9f)
                line(canvas, 12f, 9f, 12f, 15f)
                paint.strokeWidth = 2f
            }
            StrictIcon.REPEAT_LIST -> {
                repeatBase(canvas)
                paint.strokeWidth = 1.15f
                line(canvas, 9.5f, 10f, 14.5f, 10f)
                line(canvas, 9.5f, 12f, 14.5f, 12f)
                line(canvas, 9.5f, 14f, 14.5f, 14f)
                paint.strokeWidth = 2f
            }
            StrictIcon.TIMER -> {
                circle(canvas, 12f, 13f, 8f); line(canvas, 12f, 13f, 16f, 9f); line(canvas, 9f, 3f, 15f, 3f)
            }
            StrictIcon.SPEED -> {
                arc(canvas, 4f, 5f, 20f, 21f, 195f, 150f); line(canvas, 12f, 17f, 16f, 11f)
            }
            StrictIcon.EQUALIZER -> {
                line(canvas, 4f, 6f, 9f, 6f); line(canvas, 15f, 6f, 20f, 6f); circle(canvas, 12f, 6f, 2f)
                line(canvas, 4f, 12f, 13f, 12f); line(canvas, 19f, 12f, 20f, 12f); circle(canvas, 16f, 12f, 2f)
                line(canvas, 4f, 18f, 6f, 18f); line(canvas, 12f, 18f, 20f, 18f); circle(canvas, 9f, 18f, 2f)
            }
            StrictIcon.LEVEL, StrictIcon.AUDIO_PROCESS -> {
                path.moveTo(3f, 12f); path.lineTo(5f, 12f); path.lineTo(7f, 6f)
                path.lineTo(10f, 18f); path.lineTo(13f, 4f); path.lineTo(16f, 20f)
                path.lineTo(18f, 12f); path.lineTo(21f, 12f); canvas.drawPath(path, paint)
            }
            StrictIcon.ADD -> lines(canvas, 12f, 5f, 12f, 19f, 5f, 12f, 19f, 12f)
            StrictIcon.FOLDER -> {
                path.moveTo(3f, 7f); path.lineTo(10f, 7f); path.lineTo(12f, 9f)
                path.lineTo(21f, 9f); path.lineTo(21f, 19f); path.lineTo(3f, 19f); path.close()
                canvas.drawPath(path, paint)
            }
            StrictIcon.PLAYLIST_ADD, StrictIcon.LIST_ADD -> {
                lines(canvas, 4f, 7f, 14f, 7f, 4f, 12f, 12f, 12f, 4f, 17f, 12f, 17f)
                lines(canvas, 18f, 12f, 18f, 20f, 14f, 16f, 22f, 16f)
            }
            StrictIcon.SEARCH -> {
                circle(canvas, 10f, 10f, 6f); line(canvas, 15f, 15f, 20f, 20f)
            }
            StrictIcon.HEART, StrictIcon.HEART_OUTLINE -> heart(canvas)
            StrictIcon.LIST -> {
                lines(canvas, 8f, 6f, 20f, 6f, 8f, 12f, 20f, 12f, 8f, 18f, 20f, 18f)
                circles(canvas, floatArrayOf(4f, 6f, 4f, 12f, 4f, 18f), 0.7f)
            }
            StrictIcon.CLEAR_LIST -> {
                lines(canvas, 4f, 6f, 14f, 6f, 4f, 12f, 12f, 12f, 4f, 18f, 12f, 18f)
                lines(canvas, 16f, 13f, 21f, 18f, 21f, 13f, 16f, 18f)
            }
            StrictIcon.REMOVE -> line(canvas, 5f, 12f, 19f, 12f)
            StrictIcon.EDIT -> {
                path.moveTo(4f, 20f); path.lineTo(8f, 19f); path.lineTo(19f, 8f)
                path.lineTo(16f, 5f); path.lineTo(5f, 16f); path.close(); canvas.drawPath(path, paint)
            }
            StrictIcon.CHECK -> {
                path.moveTo(5f, 12f); path.lineTo(10f, 17f); path.lineTo(20f, 7f); canvas.drawPath(path, paint)
            }
            StrictIcon.BACK -> {
                line(canvas, 5f, 12f, 19f, 12f); path.moveTo(10f, 7f); path.lineTo(5f, 12f); path.lineTo(10f, 17f); canvas.drawPath(path, paint)
            }
            StrictIcon.CLOSE -> lines(canvas, 6f, 6f, 18f, 18f, 18f, 6f, 6f, 18f)
            StrictIcon.DRAG -> circles(canvas, floatArrayOf(9f, 6f, 15f, 6f, 9f, 12f, 15f, 12f, 9f, 18f, 15f, 18f), 0.9f)
            StrictIcon.UNDO -> undo(canvas, false)
            StrictIcon.REDO -> undo(canvas, true)
            StrictIcon.LOCK -> {
                lines(canvas, 4f, 9f, 4f, 4f, 4f, 4f, 9f, 4f, 15f, 4f, 20f, 4f, 20f, 4f, 20f, 9f, 20f, 15f, 20f, 20f, 20f, 20f, 15f, 20f, 9f, 20f, 4f, 20f, 4f, 20f, 4f, 15f)
                circle(canvas, 12f, 12f, 3f)
            }
            StrictIcon.VOLUME -> volume(canvas, false)
            StrictIcon.MUTE -> volume(canvas, true)
            StrictIcon.CUT -> {
                circle(canvas, 6f, 7f, 3f); circle(canvas, 6f, 17f, 3f)
                line(canvas, 8.5f, 8.5f, 20f, 16f); line(canvas, 8.5f, 15.5f, 20f, 8f)
            }
            StrictIcon.SPLIT -> {
                path.moveTo(5f, 4f); path.lineTo(5f, 10f); path.cubicTo(5f, 13f, 7f, 14f, 9f, 14f); path.lineTo(19f, 14f)
                path.moveTo(15f, 10f); path.lineTo(19f, 14f); path.lineTo(15f, 18f); path.moveTo(5f, 20f); path.lineTo(5f, 16f)
                canvas.drawPath(path, paint)
            }
            StrictIcon.DELETE -> {
                line(canvas, 5f, 7f, 19f, 7f); line(canvas, 9f, 7f, 9f, 4f); line(canvas, 9f, 4f, 15f, 4f); line(canvas, 15f, 4f, 15f, 7f)
                path.moveTo(7f, 7f); path.lineTo(8f, 20f); path.lineTo(16f, 20f); path.lineTo(17f, 7f); canvas.drawPath(path, paint)
                paint.strokeWidth = 1.15f
                line(canvas, 10.25f, 11f, 10.25f, 16f)
                line(canvas, 13.75f, 11f, 13.75f, 16f)
                paint.strokeWidth = 2f
            }
            StrictIcon.MUSIC -> {
                line(canvas, 10f, 5f, 10f, 17f); line(canvas, 10f, 5f, 18f, 3f); line(canvas, 18f, 3f, 18f, 14f)
                circle(canvas, 7f, 18f, 3f); circle(canvas, 15f, 15f, 3f)
            }
        }
    }

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) =
        canvas.drawLine(x1, y1, x2, y2, paint)

    private fun lines(canvas: Canvas, vararg points: Float) {
        var index = 0
        while (index + 3 < points.size) {
            line(canvas, points[index], points[index + 1], points[index + 2], points[index + 3])
            index += 4
        }
    }

    private fun polygon(canvas: Canvas, vararg points: Float) {
        path.moveTo(points[0], points[1])
        var index = 2
        while (index + 1 < points.size) {
            path.lineTo(points[index], points[index + 1]); index += 2
        }
        path.close(); canvas.drawPath(path, paint)
    }

    private fun rect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) =
        canvas.drawRect(left, top, right, bottom, paint)

    private fun circle(canvas: Canvas, x: Float, y: Float, radius: Float) = canvas.drawCircle(x, y, radius, paint)

    private fun circles(canvas: Canvas, points: FloatArray, radius: Float) {
        var index = 0
        while (index + 1 < points.size) {
            circle(canvas, points[index], points[index + 1], radius); index += 2
        }
    }

    private fun arc(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, start: Float, sweep: Float) =
        canvas.drawArc(left, top, right, bottom, start, sweep, false, paint)

    private fun curve(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        cx1: Float,
        cy1: Float,
        cx2: Float,
        cy2: Float,
        x2: Float,
        y2: Float,
    ) {
        path.moveTo(x1, y1); path.cubicTo(cx1, cy1, cx2, cy2, x2, y2); canvas.drawPath(path, paint); path.reset()
    }

    private fun chevronRight(canvas: Canvas, x: Float, y: Float) {
        path.moveTo(x, y - 3f); path.lineTo(x + 3f, y); path.lineTo(x, y + 3f); canvas.drawPath(path, paint); path.reset()
    }

    private fun heart(canvas: Canvas) {
        path.moveTo(12f, 20f)
        path.cubicTo(9f, 18f, 4f, 14f, 4f, 9f)
        path.cubicTo(4f, 5f, 9f, 4f, 12f, 7f)
        path.cubicTo(15f, 4f, 20f, 5f, 20f, 9f)
        path.cubicTo(20f, 14f, 15f, 18f, 12f, 20f)
        canvas.drawPath(path, paint)
    }

    private fun repeatBase(canvas: Canvas) {
        path.moveTo(17f, 4f); path.lineTo(20f, 7f); path.lineTo(17f, 10f)
        path.moveTo(20f, 7f); path.lineTo(8f, 7f); path.cubicTo(5f, 7f, 4f, 9f, 4f, 11f)
        path.moveTo(7f, 20f); path.lineTo(4f, 17f); path.lineTo(7f, 14f)
        path.moveTo(4f, 17f); path.lineTo(16f, 17f); path.cubicTo(19f, 17f, 20f, 15f, 20f, 13f)
        canvas.drawPath(path, paint)
        path.reset()
    }

    private fun undo(canvas: Canvas, mirrored: Boolean) {
        val save = canvas.save()
        if (mirrored) { canvas.scale(-1f, 1f, 12f, 12f) }
        path.moveTo(9f, 7f); path.lineTo(4f, 7f); path.lineTo(8f, 3f)
        path.moveTo(4f, 7f); path.lineTo(14f, 7f); path.cubicTo(18f, 7f, 20f, 10f, 20f, 13f)
        path.cubicTo(20f, 17f, 17f, 19f, 14f, 19f); path.lineTo(9f, 19f)
        canvas.drawPath(path, paint); canvas.restoreToCount(save)
    }

    private fun volume(canvas: Canvas, muted: Boolean) {
        path.moveTo(4f, 10f); path.lineTo(8f, 10f); path.lineTo(13f, 6f)
        path.lineTo(13f, 18f); path.lineTo(8f, 14f); path.lineTo(4f, 14f); path.close(); canvas.drawPath(path, paint); path.reset()
        if (muted) {
            lines(canvas, 17f, 10f, 22f, 15f, 22f, 10f, 17f, 15f)
        } else {
            arc(canvas, 14f, 8f, 20f, 16f, -55f, 110f)
            arc(canvas, 14f, 5f, 24f, 19f, -55f, 110f)
        }
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    override fun isStateful(): Boolean = colors.isStateful
    override fun onStateChange(state: IntArray): Boolean {
        val color = colors.getColorForState(state, colors.defaultColor)
        if (paint.color == color) return false
        paint.color = color
        invalidateSelf()
        return true
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = (24f * density).toInt()
    override fun getIntrinsicHeight(): Int = (24f * density).toInt()
}

/** Wrapper that keeps a 24 dp icon exactly in the button center. */
internal class CenteredStrictIconDrawable(
    private val icon: Drawable,
) : Drawable() {
    override fun draw(canvas: Canvas) {
        val width = icon.intrinsicWidth.coerceAtMost(bounds.width())
        val height = icon.intrinsicHeight.coerceAtMost(bounds.height())
        val left = bounds.left + (bounds.width() - width) / 2
        val top = bounds.top + (bounds.height() - height) / 2
        icon.bounds = android.graphics.Rect(left, top, left + width, top + height)
        icon.state = state
        icon.draw(canvas)
    }

    override fun setAlpha(alpha: Int) { icon.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { icon.colorFilter = colorFilter; invalidateSelf() }
    override fun isStateful(): Boolean = icon.isStateful
    override fun onStateChange(state: IntArray): Boolean {
        val changed = icon.setState(state)
        if (changed) invalidateSelf()
        return changed
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = icon.intrinsicWidth
    override fun getIntrinsicHeight(): Int = icon.intrinsicHeight
}
