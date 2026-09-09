package com.dumuzeyn.mp3player

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

internal class ThemeController(private val host: MainActivityCore) {
    private var launcherUpdatePending = false

    fun load(preferences: SharedPreferences) {
        host.appearanceState.themeMode = preferences.getString(THEME, "light") ?: "light"
        if (host.appearanceState.themeMode !in VALID_THEMES) {
            host.appearanceState.themeMode = "light"
        }
        host.appearanceState.customBg = preferences.getInt(CUSTOM_BG, Color.WHITE)
        host.appearanceState.customFg = preferences.getInt(CUSTOM_FG, Color.BLACK)
        host.appearanceState.customSecondaryAccent = preferences.getInt(
            CUSTOM_SECONDARY_ACCENT,
            VoltunePalette.GOLD,
        )
    }

    fun themeName(): String = when (host.appearanceState.themeMode) {
        "dark" -> host.tr("Dark", "Темная")
        "custom" -> host.tr("Custom", "Своя")
        "system" -> host.tr("System", "Системная")
        else -> host.tr("Light", "Светлая")
    }

    fun applyPalette() {
        val state = host.appearanceState
        state.dark = isDarkTheme(state.themeMode, state.customBg, isSystemDark(host))
        when {
            state.themeMode == "custom" -> applyCustomPalette()
            state.dark -> applyDarkPalette()
            else -> applyLightPalette()
        }
        if (state.themeMode == "custom" && state.customTextColor != 0) {
            host.fg = state.customTextColor
            host.primaryText = state.customTextColor
            host.secondaryText = mixColor(state.customTextColor, host.bg, 0.58f)
            if (ThemeContrastPolicy.requiresOutline(state.customTextColor, host.bg)) {
                state.textOutlineEnabled = true
            }
        }
        host.muted = host.secondaryText
        host.line = host.cardStroke
        host.panel = host.card
    }

    private fun applyCustomPalette() {
        val state = host.appearanceState
        host.bg = state.customBg
        host.fg = state.customFg
        host.primaryText = state.customFg
        host.secondaryText = mixColor(state.customFg, state.customBg, 0.58f)
        host.card = mixColor(state.customBg, state.customFg, if (state.dark) 0.92f else 0.96f)
        host.cardStroke = mixColor(state.customFg, state.customBg, 0.18f)
        host.purple = state.customFg
        host.purpleDark = mixColor(state.customFg, state.customBg, 0.82f)
        host.purpleSoft = mixColor(state.customFg, state.customBg, 0.18f)
        host.yellow = state.customSecondaryAccent
        host.yellowDark = mixColor(state.customSecondaryAccent, state.customBg, 0.82f)
        host.yellowSoft = mixColor(state.customSecondaryAccent, state.customBg, 0.18f)
    }

    private fun applyDarkPalette() {
        host.bg = host.getColor(R.color.voltune_background_dark)
        host.fg = host.getColor(R.color.voltune_text_dark)
        host.primaryText = host.getColor(R.color.voltune_text_dark)
        host.secondaryText = host.getColor(R.color.voltune_text_secondary_dark)
        host.card = host.getColor(R.color.voltune_surface_dark)
        host.cardStroke = host.getColor(R.color.voltune_stroke_dark)
        host.purple = host.getColor(R.color.voltune_primary_dark)
        host.purpleDark = host.getColor(R.color.voltune_primary_strong_dark)
        host.purpleSoft = host.getColor(R.color.voltune_primary_soft_dark)
        host.yellow = host.getColor(R.color.voltune_secondary_dark)
        host.yellowDark = host.getColor(R.color.voltune_secondary_strong)
        host.yellowSoft = host.getColor(R.color.voltune_secondary_soft_dark)
    }

    private fun applyLightPalette() {
        host.bg = host.getColor(R.color.voltune_background_light)
        host.fg = host.getColor(R.color.voltune_text_light)
        host.primaryText = host.getColor(R.color.voltune_text_light)
        host.secondaryText = host.getColor(R.color.voltune_text_secondary_light)
        host.card = host.getColor(R.color.voltune_surface_light)
        host.cardStroke = host.getColor(R.color.voltune_stroke_light)
        host.purple = host.getColor(R.color.voltune_primary_light)
        host.purpleDark = host.getColor(R.color.voltune_primary_strong_light)
        host.purpleSoft = host.getColor(R.color.voltune_primary_soft_light)
        host.yellow = host.getColor(R.color.voltune_secondary_light)
        host.yellowDark = host.getColor(R.color.voltune_secondary_strong)
        host.yellowSoft = host.getColor(R.color.voltune_secondary_soft_light)
    }

    @Suppress("DEPRECATION")
    fun applyWindow() {
        host.window.setBackgroundDrawable(ColorDrawable(host.bg))
        host.window.statusBarColor = host.bg
        host.window.navigationBarColor = host.bg
        if (Build.VERSION.SDK_INT >= 23) {
            var lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) {
                lightBars = lightBars or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            host.window.decorView.systemUiVisibility = if (host.appearanceState.dark) 0 else lightBars
        }
        updateTaskPreview()
    }

    fun openDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(host.tr("Theme", "Тема")),
            host.uiFactory.dialogTitleParams(),
        )
        val controls = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        addChoice(controls, host.tr("Light", "Светлая"), "light")
        addChoice(controls, host.tr("Dark", "Темная"), "dark")
        addChoice(controls, host.tr("System", "Системная"), "system")
        addChoice(controls, host.tr("Custom", "Своя"), "custom")
        if (host.appearanceState.themeMode == "custom") {
            addCustomControls(controls)
            val scroll = ScrollView(host)
            scroll.addView(controls, FrameLayout.LayoutParams(-1, -2))
            panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            panel.addView(controls, LinearLayout.LayoutParams(-1, -2))
        }
        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener {
            if (shade.parent != null) host.overlayHost.removeView(shade)
            host.playerUiController.updateMini()
        }
        panel.addView(
            done,
            LinearLayout.LayoutParams(-1, host.dp(48)).apply {
                setMargins(0, host.dp(8), 0, 0)
            },
        )
        val panelHeight = if (host.appearanceState.themeMode == "custom") {
            minOf(host.dp(650), host.resources.displayMetrics.heightPixels - host.dp(44))
        } else {
            -2
        }
        shade.addView(panel, host.centerParams(host.dp(340), panelHeight))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun addCustomControls(controls: LinearLayout) {
        addColorControl(controls, host.tr("Background", "Фон"), COLOR_BACKGROUND, 30)
        addColorControl(controls, host.tr("Accent", "Акцент"), COLOR_ACCENT, 30)
        addColorControl(
            controls,
            host.tr("Second accent", "Второй акцент"),
            COLOR_SECONDARY_ACCENT,
            30,
        )
        addColorControl(controls, host.tr("Text", "Текст"), COLOR_TEXT, 30)

        val state = host.appearanceState
        val outlineToggle = host.uiFactory.button(
            host.tr("Text outline: ", "Контур текста: ") +
                host.tr(if (state.textOutlineEnabled) "on" else "off", if (state.textOutlineEnabled) "вкл" else "выкл"),
        )
        host.uiFactory.applySecondaryButtonStyle(outlineToggle)
        outlineToggle.setOnClickListener {
            state.textOutlineEnabled = !state.textOutlineEnabled
            applyTheme(state.themeMode)
        }
        controls.addView(
            outlineToggle,
            LinearLayout.LayoutParams(-1, host.dp(46)).apply {
                setMargins(0, host.dp(8), 0, host.dp(8))
            },
        )
        if (state.customTextColor != 0 &&
            ThemeContrastPolicy.requiresOutline(state.customTextColor, host.bg)
        ) {
            controls.addView(
                host.uiFactory.text(
                    host.tr(
                        "The outline is enabled automatically because the selected text color " +
                            "has low contrast. Your color is unchanged.",
                        "Контур включён автоматически из-за низкого контраста. " +
                            "Выбранный цвет текста не изменён.",
                    ),
                    13,
                    false,
                ),
                LinearLayout.LayoutParams(-1, -2),
            )
        }
        if (state.textOutlineEnabled) {
            addColorControl(
                controls,
                host.tr("Outline color", "Цвет контура"),
                COLOR_OUTLINE,
                32,
                labelTop = 4,
                labelBottom = 2,
            )
        }
    }

    private fun addColorControl(
        parent: LinearLayout,
        label: String,
        target: Int,
        labelHeight: Int,
        labelTop: Int = 0,
        labelBottom: Int = 0,
    ) {
        parent.addView(
            host.uiFactory.text(label, 16, true),
            LinearLayout.LayoutParams(-1, host.dp(labelHeight)).apply {
                setMargins(0, host.dp(labelTop), 0, host.dp(labelBottom))
            },
        )
        addColorButton(parent, target)
    }

    private fun addChoice(parent: LinearLayout, label: String, mode: String) {
        val button = host.uiFactory.button(label)
        button.textSize = 17f
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.setPadding(host.dp(18), 0, host.dp(12), 0)
        if (mode == host.appearanceState.themeMode) {
            host.uiFactory.applyPrimaryButtonStyle(button)
        } else {
            host.uiFactory.applySecondaryButtonStyle(button)
        }
        button.setOnClickListener { applyTheme(mode) }
        parent.addView(
            button,
            LinearLayout.LayoutParams(-1, host.dp(48)).apply {
                setMargins(0, host.dp(3), 0, host.dp(3))
            },
        )
    }

    private fun addColorButton(parent: LinearLayout, target: Int) {
        val color = colorForTarget(target)
        val button = host.uiFactory.button(colorHex(color))
        button.setTextColor(ThemeManager.readableOn(color))
        host.uiFactory.setSurface(button, color, false)
        button.setOnClickListener {
            host.overlayHost.removeAllViews()
            openColorPicker(target)
        }
        parent.addView(
            button,
            LinearLayout.LayoutParams(-1, host.dp(36)).apply {
                setMargins(0, host.dp(2), 0, host.dp(2))
            },
        )
    }

    private fun openColorPicker(target: Int) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(colorTargetName(target)),
            host.uiFactory.dialogTitleParams(),
        )
        val preview = View(host).apply { setBackgroundColor(colorForTarget(target)) }
        panel.addView(preview, LinearLayout.LayoutParams(-1, host.dp(34)))
        val wheel = ThemeColorWheelView(host, colorForTarget(target)) { color ->
            when (target) {
                COLOR_BACKGROUND -> {
                    host.appearanceState.themeMode = "custom"
                    host.appearanceState.customBg = color
                }
                COLOR_ACCENT -> {
                    host.appearanceState.themeMode = "custom"
                    host.appearanceState.customFg = color
                }
                COLOR_SECONDARY_ACCENT -> {
                    host.appearanceState.themeMode = "custom"
                    host.appearanceState.customSecondaryAccent = color
                }
                COLOR_OUTLINE -> host.appearanceState.textOutlineColor = color
                else -> host.appearanceState.customTextColor = color
            }
            preview.setBackgroundColor(color)
        }
        panel.addView(
            wheel,
            LinearLayout.LayoutParams(-1, host.dp(280)).apply {
                setMargins(0, host.dp(12), 0, host.dp(12))
            },
        )
        val actions = host.uiFactory.row()
        val back = host.uiFactory.button(host.tr("Back", "Назад"))
        back.setOnClickListener {
            host.overlayHost.removeView(shade)
            openDialog()
        }
        actions.addView(back, LinearLayout.LayoutParams(0, host.dp(54), 1f))
        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener {
            val mode = if (target in CUSTOM_COLOR_TARGETS) {
                "custom"
            } else {
                host.appearanceState.themeMode
            }
            applyTheme(mode)
        }
        actions.addView(done, LinearLayout.LayoutParams(0, host.dp(54), 1f))
        panel.addView(actions)
        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
    }

    private fun colorForTarget(target: Int): Int = when (target) {
        COLOR_BACKGROUND -> host.appearanceState.customBg
        COLOR_ACCENT -> host.appearanceState.customFg
        COLOR_SECONDARY_ACCENT -> host.appearanceState.customSecondaryAccent
        COLOR_OUTLINE -> effectiveOutlineColor()
        else -> host.appearanceState.customTextColor.takeIf { it != 0 } ?: host.primaryText
    }

    private fun colorTargetName(target: Int): String = when (target) {
        COLOR_BACKGROUND -> host.tr("Background", "Фон")
        COLOR_ACCENT -> host.tr("Accent", "Акцент")
        COLOR_SECONDARY_ACCENT -> host.tr("Second accent", "Второй акцент")
        COLOR_OUTLINE -> host.tr("Outline color", "Цвет контура")
        else -> host.tr("Text", "Текст")
    }

    fun applyTextOutline(text: TextView) {
        text.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        val lightTheme = host.appearanceState.themeMode == "light"
        val darkTheme = host.appearanceState.themeMode == "dark"
        val width = host.resources.displayMetrics.density *
            if (lightTheme || darkTheme) 0.65f else 0.25f
        val enabled = lightTheme || darkTheme || host.appearanceState.textOutlineEnabled
        val color = when {
            lightTheme -> Color.WHITE
            darkTheme -> Color.BLACK
            else -> effectiveOutlineColor()
        }
        when (text) {
            is OutlinedTextView -> text.setTextOutline(enabled, color, width)
            is OutlinedButton -> text.setTextOutline(enabled, color, width)
        }
    }

    private fun effectiveOutlineColor(): Int =
        host.appearanceState.textOutlineColor.takeIf { it != 0 }
            ?: ThemeManager.readableOn(host.primaryText)

    private fun applyTheme(mode: String) {
        host.appearanceState.themeMode = mode
        host.appearanceState.dark = isDarkTheme(
            mode,
            host.appearanceState.customBg,
            isSystemDark(host),
        )
        host.saveState()
        host.refreshPlaybackAppearance()
        host.overlayHost?.removeAllViews()
        host.rebuildUiForTheme()
        openDialog()
        launcherUpdatePending = true
    }

    fun onHostStopped() {
        if (!launcherUpdatePending) return
        launcherUpdatePending = false
        updateLauncherIcon()
    }

    fun syncLauncherIcon() {
        launcherUpdatePending = true
    }

    fun updateLauncherIcon() {
        val state = host.appearanceState
        val useDark = isDarkTheme(state.themeMode, state.customBg, isSystemDark(host))
        val selected = LauncherComponents.forThemeState(
            host,
            state.themeMode,
            useDark,
            state.customBg,
            state.customFg,
            state.customSecondaryAccent,
        )
        try {
            LauncherComponents.apply(host, selected)
        } catch (_: RuntimeException) {
            // A launcher may reject alias changes while the task is visible.
        }
    }

    @Suppress("DEPRECATION")
    private fun updateTaskPreview() {
        if (Build.VERSION.SDK_INT < 21) return
        try {
            host.setTaskDescription(
                ActivityManager.TaskDescription(
                    host.getString(R.string.app_name),
                    launcherPreviewIcon(),
                    host.bg,
                ),
            )
        } catch (_: RuntimeException) {
        }
    }

    private fun launcherPreviewIcon(): Bitmap = AppIconRenderer.renderPreview(
        host,
        host.bg,
        host.purple,
        host.yellow,
        maxOf(1, host.dp(64)),
    )

    private fun colorHex(color: Int): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    companion object {
        private const val COLOR_BACKGROUND = 0
        private const val COLOR_ACCENT = 1
        private const val COLOR_SECONDARY_ACCENT = 2
        private const val COLOR_TEXT = 3
        private const val COLOR_OUTLINE = 4
        private const val THEME = "theme"
        private const val CUSTOM_BG = "customBg"
        private const val CUSTOM_FG = "customFg"
        private const val CUSTOM_SECONDARY_ACCENT = "customSecondaryAccent"

        private val VALID_THEMES = setOf("light", "dark", "system", "custom")
        private val CUSTOM_COLOR_TARGETS = setOf(
            COLOR_BACKGROUND,
            COLOR_ACCENT,
            COLOR_SECONDARY_ACCENT,
        )

        @JvmStatic
        fun isDarkTheme(themeMode: String, customBackground: Int, systemDark: Boolean): Boolean =
            themeMode == "dark" ||
                themeMode == "system" && systemDark ||
                themeMode == "custom" && ThemeManager.isDarkColor(customBackground)

        @JvmStatic
        fun isSystemDark(context: Context): Boolean {
            val nightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            return nightMode == Configuration.UI_MODE_NIGHT_YES
        }

        @JvmStatic
        fun mixColor(first: Int, second: Int, amount: Float): Int =
            ThemeManager.mixColor(first, second, amount)
    }
}
