package com.dumuzeyn.mp3player

import android.content.res.ColorStateList
import android.graphics.Color
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

    fun icon(symbol: String): Button = button("").apply {
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
            this,
            TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE,
        )
        setSingleLine(true)
        textSize = 14f
        setBackgroundColor(Color.TRANSPARENT)
        TextOutlinePolicy.markCardSurface(this, false)
        val mapped = StrictIcon.fromLegacy(symbol)
        if (mapped == null) {
            text = symbol
        } else {
            StrictIconButtonStyler.apply(this, mapped)
        }
    }

    fun icon(icon: StrictIcon): Button = button("").apply {
        TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
        setSingleLine(true)
        setBackgroundColor(Color.TRANSPARENT)
        TextOutlinePolicy.markCardSurface(this, false)
        StrictIconButtonStyler.apply(this, icon)
    }

    fun setIcon(button: Button, icon: StrictIcon) {
        StrictIconButtonStyler.apply(button, icon)
    }

    fun setLabeledIcon(button: Button, icon: StrictIcon, label: String, above: Boolean = false) {
        StrictIconButtonStyler.applyLabeled(button, icon, label, above)
    }

    fun setIconOnBackground(button: Button, icon: StrictIcon, background: GradientDrawable) {
        StrictIconButtonStyler.applyOnBackground(button, icon, background)
    }

    fun shuffleButton(): Button = icon(StrictIcon.SHUFFLE)

    fun applyPlainIcon(button: Button, color: Int) {
        button.setTextColor(color)
        button.setBackgroundColor(Color.TRANSPARENT)
        TextOutlinePolicy.markCardSurface(button, false)
        StrictIconButtonStyler.refreshTint(button)
        button.elevation = 0f
        button.translationZ = 0f
    }

    fun applyPrimary(button: Button) {
        if (button.getTag(R.id.strict_button_icon) is StrictIcon && button.text.isNullOrEmpty()) {
            applyPlainIcon(button, host.purple)
            return
        }
        button.setTextColor(Color.WHITE)
        button.background = background(host.purple, false)
        StrictIconButtonStyler.refreshTint(button)
        TextOutlinePolicy.markCardSurface(button, true)
    }

    fun applySecondary(button: Button) {
        applySecondary(button, host.appearanceState.cardOpacity)
    }

    fun applySecondary(button: Button, opacity: Int) {
        if (button.getTag(R.id.strict_button_icon) is StrictIcon && button.text.isNullOrEmpty()) {
            applyPlainIcon(button, host.primaryText)
            return
        }
        button.setTextColor(host.primaryText)
        val drawable = background(host.cardSurfaceColor(host.card, opacity), true).apply {
            setStroke(host.dp(1), host.cardStroke)
        }
        button.background = drawable
        StrictIconButtonStyler.refreshTint(button)
        TextOutlinePolicy.markCardSurface(button, true)
    }

    fun applyPlayerTool(button: Button, active: Boolean) {
        button.setSingleLine(true)
        button.maxLines = 1
        button.ellipsize = null
        button.textSize = 14f
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            button,
            10,
            14,
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
        button.setTextColor(if (active) host.yellow else host.primaryText)
        val drawable = background(
            host.cardSurfaceColor(host.card, host.appearanceState.cardOpacity.coerceAtLeast(68)),
            false,
        ).apply {
            setStroke(host.dp(1), if (active) host.purple else host.cardStroke)
        }
        button.background = drawable
        StrictIconButtonStyler.refreshTint(button)
        TextOutlinePolicy.markCardSurface(button, true)
    }

    private fun background(color: Int, outlined: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = host.dp(8).toFloat()
            if (outlined) setStroke(host.dp(1), host.cardStroke)
        }

    private fun rippleMask(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = host.dp(8).toFloat()
    }
}
