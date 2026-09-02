package com.dumuzeyn.mp3player

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG contrast check which preserves a selected text color and requests an outline. */
object ThemeContrastPolicy {
    private const val MINIMUM_BODY_TEXT_CONTRAST = 4.5

    @JvmStatic
    fun requiresOutline(textColor: Int, backgroundColor: Int): Boolean =
        contrastRatio(textColor, backgroundColor) < MINIMUM_BODY_TEXT_CONTRAST

    @JvmStatic
    fun outlineIsDistinct(textColor: Int, outlineColor: Int): Boolean =
        contrastRatio(textColor, outlineColor) >= 1.35

    @JvmStatic
    fun contrastRatio(first: Int, second: Int): Double {
        val lighter = max(luminance(first), luminance(second))
        val darker = min(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Int): Double =
        0.2126 * channel(color shr 16 and 0xff) +
            0.7152 * channel(color shr 8 and 0xff) +
            0.0722 * channel(color and 0xff)

    private fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }
}
