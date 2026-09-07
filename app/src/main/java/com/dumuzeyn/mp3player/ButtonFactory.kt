package com.dumuzeyn.mp3player

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import androidx.core.widget.TextViewCompat

internal class ButtonFactory(private val host: MainActivityCore) {
    fun button(label: String): Button = OutlinedButton(host).apply {
        text = label
        setTextColor(host.fg)
        textSize = 14f
        isAllCaps = false
        gravity = Gravity.CENTER
        includeFontPadding = true
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        elevation = 0f
        translationZ = 0f
        minWidth = 0
        minHeight = 0
        setSingleLine(false)
        maxLines = 2
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this,
            10,
            14,
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
        setBackgroundColor(Color.TRANSPARENT)
        foreground = RippleDrawable(
            ColorStateList.valueOf(
                Color.argb(42, Color.red(host.fg), Color.green(host.fg), Color.blue(host.fg)),
            ),
            null,
            rippleMask(),
        )
        host.themeController.applyTextOutline(this)
    }

    fun icon(symbol: String): Button = button(symbol).apply {
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
            this,
            TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE,
        )
        setSingleLine(true)
        textSize = 24f
    }

    fun shuffleButton(): Button = icon("⇄").apply {
        textSize = 27f
        typeface = Typeface.DEFAULT_BOLD
    }

    fun applyPlainIcon(button: Button, color: Int) {
        button.setTextColor(color)
        button.setBackgroundColor(Color.TRANSPARENT)
        TextOutlinePolicy.markCardSurface(button, false)
        button.elevation = 0f
        button.translationZ = 0f
    }

    fun applyPrimary(button: Button) {
        button.setTextColor(Color.WHITE)
        button.background = background(host.purple, false)
        TextOutlinePolicy.markCardSurface(button, true)
    }

    fun applySecondary(button: Button) {
        applySecondary(button, host.appearanceState.cardOpacity)
    }

    fun applySecondary(button: Button, opacity: Int) {
        button.setTextColor(host.primaryText)
        val drawable = background(host.cardSurfaceColor(host.card, opacity), true).apply {
            setStroke(host.dp(1), host.cardStroke)
        }
        button.background = drawable
        TextOutlinePolicy.markCardSurface(button, true)
    }

    fun applyPlayerTool(button: Button, active: Boolean) {
        button.setSingleLine(true)
        button.textSize = 14f
        button.setTextColor(if (active) host.yellow else host.primaryText)
        val drawable = background(
            host.cardSurfaceColor(host.card, host.appearanceState.cardOpacity.coerceAtLeast(68)),
            false,
        ).apply {
            setStroke(host.dp(1), if (active) host.purple else host.cardStroke)
        }
        button.background = drawable
        TextOutlinePolicy.markCardSurface(button, true)
    }

    private fun background(color: Int, outlined: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = host.dp(16).toFloat()
            if (outlined) setStroke(host.dp(1), host.cardStroke)
        }

    private fun rippleMask(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = host.dp(16).toFloat()
    }
}
