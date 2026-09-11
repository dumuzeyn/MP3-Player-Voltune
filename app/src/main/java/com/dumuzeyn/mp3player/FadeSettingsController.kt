package com.dumuzeyn.mp3player

import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.Switch

internal class FadeSettingsController(private val host: MainActivityCore) {
    private val preferences get() = host.getSharedPreferences(PlaybackFadePolicy.PREFS, 0)

    fun label(): String = host.tr("Track fade-out: ", "Затихание в конце: ") + if (enabled()) {
        "${seconds()} " + host.tr("s", "с")
    } else host.tr("off", "выкл")

    private fun enabled() = preferences.getBoolean(PlaybackFadePolicy.ENABLED, false)
    private fun seconds() = preferences.getInt(PlaybackFadePolicy.SECONDS, PlaybackFadePolicy.DEFAULT_SECONDS)
        .coerceIn(1, 12)

    fun openDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(host.tr("Track fade-out", "Затихание в конце")),
            host.uiFactory.dialogTitleParams())
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(host).apply { addView(content) }, LinearLayout.LayoutParams(-1, -2, 1f))
        val toggle = Switch(host).apply {
            text = host.tr("Enabled", "Включено")
            setTextColor(host.primaryText)
            isChecked = enabled()
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                preferences.edit().putBoolean(PlaybackFadePolicy.ENABLED, checked).apply()
                host.refreshSettingsLabels()
            }
        }
        content.addView(toggle, LinearLayout.LayoutParams(-1, host.dp(52)))
        val value = host.uiFactory.text("${seconds()} " + host.tr("s", "с"), 18, true)
        content.addView(value, LinearLayout.LayoutParams(-1, host.dp(36)))
        val slider = SeekBar(host).apply {
            max = 11
            progress = seconds() - 1
            contentDescription = host.tr("Fade duration", "Длительность затихания")
        }
        host.uiFactory.applySeekBarColors(slider)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                value.text = "${progress + 1} " + host.tr("s", "с")
                preferences.edit().putInt(PlaybackFadePolicy.SECONDS, progress + 1).apply()
                host.refreshSettingsLabels()
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        content.addView(slider, LinearLayout.LayoutParams(-1, host.dp(48)))
        panel.addView(host.uiFactory.button(host.tr("Done", "Готово")).apply {
            host.uiFactory.applyPrimaryButtonStyle(this)
            setOnClickListener { host.overlayHost.removeView(shade) }
        }, LinearLayout.LayoutParams(-1, host.dp(48)))
        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
    }
}
