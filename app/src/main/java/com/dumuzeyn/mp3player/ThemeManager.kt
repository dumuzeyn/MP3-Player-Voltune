package com.dumuzeyn.mp3player

import android.graphics.Color
import kotlin.math.roundToInt

object ThemeManager {
    @JvmStatic
    fun isDarkColor(color: Int): Boolean =
        ((Color.red(color) * 299) + (Color.green(color) * 587) + (Color.blue(color) * 114)) /
            1_000 < 128

    @JvmStatic
    fun mixColor(first: Int, second: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        val red = ((Color.red(first) * clamped) + (Color.red(second) * (1f - clamped))).roundToInt()
        val green = ((Color.green(first) * clamped) + (Color.green(second) * (1f - clamped))).roundToInt()
        val blue = ((Color.blue(first) * clamped) + (Color.blue(second) * (1f - clamped))).roundToInt()
        return Color.rgb(red, green, blue)
    }

    @JvmStatic
    fun readableOn(color: Int): Int = if (isDarkColor(color)) Color.WHITE else Color.BLACK
}
