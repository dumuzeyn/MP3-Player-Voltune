package com.dumuzeyn.mp3player

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.min

internal class UiFactory(private val host: MainActivityCore) {
    private val buttons = ButtonFactory(host)

    fun row(): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun text(value: String, size: Int, bold: Boolean): TextView = OutlinedTextView(host).apply {
        text = value
        setTextColor(host.fg)
        textSize = size.toFloat()
        gravity = Gravity.CENTER_VERTICAL
        setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
        setSingleLine(false)
        host.themeController.applyTextOutline(this)
    }

    fun dialogTitle(value: String): TextView = dialogTitle(value, 22)

    fun dialogTitle(value: String, size: Int): TextView = text(value, size, true).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = host.dp(46)
        setPadding(0, host.dp(4), 0, host.dp(10))
    }

    fun dialogTitleParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    fun makeMarquee(text: TextView) {
        text.setSingleLine(true)
        text.ellipsize = TextUtils.TruncateAt.MARQUEE
        text.marqueeRepeatLimit = -1
        text.isSelected = true
        text.isFocusable = true
        text.isFocusableInTouchMode = true
    }

    fun button(label: String): Button = buttons.button(label)

    fun icon(symbol: String): Button = buttons.icon(symbol)

    fun shuffleButton(): Button = buttons.shuffleButton()

    fun applyPlainIconStyle(button: Button, color: Int) {
        buttons.applyPlainIcon(button, color)
    }

    fun applyPlainIconStyle(button: Button) {
        buttons.applyPlainIcon(
            button,
            if (host.appearanceState.dark) Color.rgb(230, 226, 236) else host.primaryText,
        )
    }

    fun cardBackground(): GradientDrawable = cardBackground(host.appearanceState.dialogCardOpacity)

    fun cardBackground(opacity: Int): GradientDrawable = GradientDrawable().apply {
        setColor(host.cardSurfaceColor(host.card, opacity))
        cornerRadius = host.dp(16).toFloat()
        setStroke(host.dp(1), host.cardStroke)
    }

    fun applyCardStyle(view: View) {
        view.background = cardBackground()
        TextOutlinePolicy.markCardSurface(view, true)
        view.elevation = host.dp(1).toFloat()
    }

    fun applyCardStyle(view: View, opacity: Int) {
        view.background = cardBackground(opacity)
        TextOutlinePolicy.markCardSurface(view, true)
        view.elevation = host.dp(1).toFloat()
    }

    fun applyPrimaryButtonStyle(button: Button) {
        buttons.applyPrimary(button)
    }

    fun applySecondaryButtonStyle(button: Button) {
        buttons.applySecondary(button)
    }

    fun applySecondaryButtonStyle(button: Button, opacity: Int) {
        buttons.applySecondary(button, opacity)
    }

    fun applyPlayerToolStyle(button: Button, active: Boolean) {
        buttons.applyPlayerTool(button, active)
    }

    fun applySeekBarColors(seekBar: SeekBar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.progressTintList = ColorStateList.valueOf(host.purple)
            seekBar.thumbTintList = ColorStateList.valueOf(host.yellow)
            seekBar.progressBackgroundTintList = ColorStateList.valueOf(host.purpleSoft)
        }
    }

    fun square(size: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(host.dp(size), host.dp(size)).apply {
            setMargins(host.dp(4), host.dp(4), host.dp(4), host.dp(4))
        }

    fun libraryArtwork(size: Int = 52): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(host.dp(size), host.dp(size)).apply {
            setMargins(host.dp(4), host.dp(2), host.dp(4), host.dp(2))
        }

    fun spaced(view: View): View {
        view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, host.dp(2), 0, host.dp(2))
        }
        return view
    }

    fun spacedLibraryCard(view: View): View {
        view.layoutParams = LinearLayout.LayoutParams(-1, libraryCardHeight()).apply {
            setMargins(0, host.dp(2), 0, host.dp(2))
        }
        return view
    }

    fun libraryCardHeight(): Int =
        host.resources.getDimensionPixelSize(R.dimen.library_card_height)

    fun setSurface(view: View, color: Int, outlined: Boolean) {
        setSurface(view, color, outlined, host.appearanceState.dialogCardOpacity)
    }

    fun setSurface(view: View, color: Int, outlined: Boolean, opacity: Int) {
        val surfaceColor = if (color == host.card || color == host.panel) {
            host.cardSurfaceColor(color, opacity)
        } else {
            color
        }
        view.background = rounded(surfaceColor, outlined)
        TextOutlinePolicy.markCardSurface(view, true)
    }

    fun lineView(): View = View(host).apply { setBackgroundColor(host.line) }

    fun coverView(): ImageView = RotatingCoverImageView(host).apply {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun staticCoverView(): ImageView = ImageView(host).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = if (host.appearanceState.circularCovers) {
                    min(view.width, view.height) * 0.5f
                } else {
                    host.dp(8).toFloat()
                }
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun shade(): FrameLayout {
        val shade = SwipeDismissFrameLayout(host)
        val channel = if (host.appearanceState.dark) 0 else 255
        shade.setBackgroundColor(Color.argb(190, channel, channel, channel))
        val dismiss = Runnable {
            if (shade.parent != null) host.overlayHost.removeView(shade)
            host.playerUiController.updateMini()
        }
        shade.setDismissAction(dismiss)
        shade.setOnClickListener { dismiss.run() }
        return shade
    }

    fun panelCard(): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(host.dp(12), host.dp(12), host.dp(12), host.dp(12))
        applyCardStyle(this)
        setOnClickListener { }
    }

    private fun rounded(color: Int, outlined: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = host.dp(if (outlined) 16 else 14).toFloat()
            setStroke(if (outlined) host.dp(1) else 0, if (outlined) host.cardStroke else color)
        }
}
