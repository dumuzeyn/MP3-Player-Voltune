package com.dumuzeyn.mp3player

import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar

/** Controls the vinyl-style cover rotation speed used by the full player. */
class CoverRotationSettingsController(private val host: MainActivityCore) {
    fun settingLabel(): String =
        host.tr("Full-player disc speed: ", "Скорость диска в плеере: ") +
            host.appearanceState.fullPlayerRotationSpeed + "%"

    fun openDialog() {
        val shade: FrameLayout = host.uiFactory.shade()
        val panel: LinearLayout = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))

        val label = host.uiFactory.text(settingLabel(), 17, true)
        label.minHeight = host.dp(52)
        label.setPadding(0, host.dp(4), 0, host.dp(8))
        panel.addView(label, LinearLayout.LayoutParams(-1, -2))

        val seek = SeekBar(host).apply {
            max = MAX_SPEED - MIN_SPEED
            progress = host.appearanceState.fullPlayerRotationSpeed - MIN_SPEED
        }
        host.uiFactory.applySeekBarColors(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                host.appearanceState.fullPlayerRotationSpeed = MIN_SPEED + progress
                label.text = settingLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                host.saveState()
            }
        })
        panel.addView(seek, LinearLayout.LayoutParams(-1, host.dp(48)))

        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener {
            host.saveState()
            host.overlayHost.removeView(shade)
            host.refreshSettingsLabels()
        }
        panel.addView(done, LinearLayout.LayoutParams(-1, host.dp(50)))

        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private companion object {
        const val MIN_SPEED = 25
        const val MAX_SPEED = 200
    }
}
