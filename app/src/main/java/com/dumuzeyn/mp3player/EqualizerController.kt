package com.dumuzeyn.mp3player

import android.content.SharedPreferences
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar

internal class EqualizerController(private val host: MainActivityCore) {
    private var playerButton: Button? = null

    fun createPlayerButton(): Button {
        val button = host.uiFactory.button(host.tr("Equalizer ≋", "Эквалайзер ≋")).apply {
            setSingleLine(false)
            maxLines = 2
            textSize = 14f
            setOnClickListener { setEnabled(!enabled()) }
            setOnLongClickListener { openDialog(); true }
        }
        playerButton = button
        refreshButton()
        return button
    }

    fun openDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(host.tr("Equalizer", "Эквалайзер")),
            host.uiFactory.dialogTitleParams(),
        )

        val enabled = host.uiFactory.button(enabledText())
        styleToggle(enabled)
        enabled.setOnClickListener {
            setEnabled(!enabled())
            enabled.text = enabledText()
            styleToggle(enabled)
        }
        panel.addView(enabled, LinearLayout.LayoutParams(-1, host.dp(48)))

        val preset = host.uiFactory.button(
            host.tr("Profile: ", "Профиль: ") + presetName(activePreset()),
        )
        host.uiFactory.applySecondaryButtonStyle(preset)
        preset.setOnClickListener {
            host.overlayHost.removeView(shade)
            openPresetDialog()
        }
        val presetParams = LinearLayout.LayoutParams(-1, host.dp(46)).apply {
            setMargins(0, host.dp(5), 0, host.dp(5))
        }
        panel.addView(preset, presetParams)

        for (band in 0 until BAND_COUNT) addBandControl(panel, band)

        val reset = host.uiFactory.button(host.tr("Reset bands", "Сбросить полосы"))
        host.uiFactory.applySecondaryButtonStyle(reset)
        reset.setOnClickListener {
            val editor = prefs().edit()
            for (band in 0 until BAND_COUNT) {
                editor.putInt(BAND_PREFIX + band, 0)
                editor.putInt(CUSTOM_BAND_PREFIX + band, 0)
            }
            editor.putString(ACTIVE_PRESET, PRESET_CUSTOM).apply()
            dispatchSettings()
            host.overlayHost.removeView(shade)
            openDialog()
        }
        val resetParams = LinearLayout.LayoutParams(-1, host.dp(48)).apply {
            setMargins(0, host.dp(8), 0, 0)
        }
        panel.addView(reset, resetParams)

        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun addBandControl(panel: LinearLayout, band: Int) {
        val value = bandLevel(band)
        val label = host.uiFactory.text(bandLabel(band, value), 14, false)
        panel.addView(label, LinearLayout.LayoutParams(-1, host.dp(28)))
        val seek = SeekBar(host).apply {
            max = MAX_DB - MIN_DB
            progress = value - MIN_DB
        }
        host.uiFactory.applySeekBarColors(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val level = progress + MIN_DB
                label.text = bandLabel(band, level)
                prefs().edit()
                    .putString(ACTIVE_PRESET, PRESET_CUSTOM)
                    .putInt(BAND_PREFIX + band, level)
                    .putInt(CUSTOM_BAND_PREFIX + band, level)
                    .apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                dispatchSettings()
            }
        })
        panel.addView(seek, LinearLayout.LayoutParams(-1, host.dp(38)))
    }

    private fun bandLabel(band: Int, level: Int): String =
        BAND_NAMES[band] + "  " + (if (level > 0) "+" else "") + level + " dB"

    private fun openPresetDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(
                host.tr("Equalizer profile", "Профиль эквалайзера"),
                21,
            ),
            host.uiFactory.dialogTitleParams(),
        )
        val selected = activePreset()
        for (presetId in PRESET_IDS) {
            val choice = host.uiFactory.button(presetName(presetId))
            if (presetId == selected) {
                host.uiFactory.applyPrimaryButtonStyle(choice)
            } else {
                host.uiFactory.applySecondaryButtonStyle(choice)
            }
            choice.setOnClickListener {
                applyPreset(presetId)
                host.overlayHost.removeView(shade)
                openDialog()
            }
            val params = LinearLayout.LayoutParams(-1, host.dp(46)).apply {
                setMargins(0, host.dp(2), 0, host.dp(2))
            }
            panel.addView(choice, params)
        }
        shade.addView(panel, host.centerParams(host.dp(330), -2))
        host.overlayHost.addView(shade)
    }

    private fun applyPreset(presetId: String) {
        val editor = prefs().edit().putString(ACTIVE_PRESET, presetId)
        if (presetId != PRESET_CUSTOM && activePreset() == PRESET_CUSTOM) {
            for (band in 0 until BAND_COUNT) {
                editor.putInt(CUSTOM_BAND_PREFIX + band, bandLevel(band))
            }
        }
        if (presetId == PRESET_CUSTOM) {
            for (band in 0 until BAND_COUNT) {
                editor.putInt(
                    BAND_PREFIX + band,
                    prefs().getInt(CUSTOM_BAND_PREFIX + band, bandLevel(band)),
                )
            }
        } else {
            val presetIndex = presetIndex(presetId)
            for (band in 0 until BAND_COUNT) {
                editor.putInt(BAND_PREFIX + band, PRESET_LEVELS[presetIndex][band])
            }
        }
        editor.apply()
        dispatchSettings()
    }

    private fun activePreset(): String =
        prefs().getString(ACTIVE_PRESET, PRESET_CUSTOM) ?: PRESET_CUSTOM

    private fun presetIndex(presetId: String): Int {
        for (index in PRESET_LEVELS.indices) {
            if (PRESET_IDS[index] == presetId) return index
        }
        return 0
    }

    private fun presetName(presetId: String): String = when (presetId) {
        "flat" -> host.tr("Flat", "Ровный")
        "bass" -> host.tr("Bass", "Бас")
        "vocal" -> host.tr("Vocal", "Вокал")
        "rock" -> host.tr("Rock", "Рок")
        "electronic" -> host.tr("Electronic", "Электроника")
        "classical" -> host.tr("Classical", "Классика")
        else -> host.tr("Custom", "Своя")
    }

    private fun bandLevel(band: Int): Int =
        prefs().getInt(BAND_PREFIX + band, 0).coerceIn(MIN_DB, MAX_DB)

    private fun enabled(): Boolean = prefs().getBoolean(ENABLED, false)

    private fun enabledText(): String = if (enabled()) {
        host.tr("Enabled", "Включён")
    } else {
        host.tr("Disabled", "Выключен")
    }

    private fun setEnabled(value: Boolean) {
        prefs().edit().putBoolean(ENABLED, value).apply()
        dispatchSettings()
        refreshButton()
    }

    private fun refreshButton() {
        playerButton?.let { host.uiFactory.applyPlayerToolStyle(it, enabled()) }
    }

    private fun styleToggle(button: Button) {
        if (enabled()) {
            host.uiFactory.applyPrimaryButtonStyle(button)
        } else {
            host.uiFactory.applySecondaryButtonStyle(button)
        }
    }

    private fun prefs(): SharedPreferences = host.getSharedPreferences(PREFS, 0)

    private fun dispatchSettings() {
        host.refreshPlaybackAppearance()
    }

    companion object {
        const val PREFS = "audio_effects"
        const val ENABLED = "equalizer_enabled"
        const val BAND_PREFIX = "equalizer_band_"
        const val BAND_COUNT = 5
        private const val CUSTOM_BAND_PREFIX = "equalizer_custom_band_"
        private const val ACTIVE_PRESET = "equalizer_preset"
        private const val PRESET_CUSTOM = "custom"
        private const val MIN_DB = -12
        private const val MAX_DB = 12
        private val BAND_NAMES = arrayOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        private val PRESET_IDS = arrayOf(
            "flat",
            "bass",
            "vocal",
            "rock",
            "electronic",
            "classical",
            PRESET_CUSTOM,
        )
        private val PRESET_LEVELS = arrayOf(
            intArrayOf(0, 0, 0, 0, 0),
            intArrayOf(7, 5, 1, -1, 1),
            intArrayOf(-3, -1, 4, 5, 2),
            intArrayOf(5, 2, -2, 3, 5),
            intArrayOf(6, 3, 0, 3, 6),
            intArrayOf(3, 1, -1, 2, 4),
        )
    }
}
