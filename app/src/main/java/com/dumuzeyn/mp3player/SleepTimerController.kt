package com.dumuzeyn.mp3player

import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.dumuzeyn.mp3player.playback.service.PlaybackSleepTimer
import kotlin.math.max

internal class SleepTimerController(private val host: MainActivityCore) {
    fun openDialog() {
        syncFromService()
        val shade: FrameLayout = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(host.tr3("Sleep timer", "Таймер сна", "◷")),
            host.uiFactory.dialogTitleParams(),
        )

        val actions = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val values = intArrayOf(5, 15, 30, host.appearanceState.customTimerMinutes)
        val labels = arrayOf("5 min", "15 min", "30 min", "${host.appearanceState.customTimerMinutes} min")
        values.indices.forEach { index ->
            val minutes = values[index]
            val button = host.uiFactory.button(labels[index])
            button.setOnClickListener {
                host.overlayHost.removeView(shade)
                start(minutes)
            }
            actions.addView(button, actionParams(4))
        }

        val custom = host.uiFactory.button(host.tr3("Custom time", "Свое время", "Custom"))
        custom.setOnClickListener {
            host.overlayHost.removeView(shade)
            openCustomDialog()
        }
        actions.addView(custom, actionParams(4))

        if (host.playbackUiState.sleepTimerEndsAt > 0) {
            val cancel = host.uiFactory.button(host.tr3("Disable timer", "Выключить таймер", "×"))
            cancel.setOnClickListener {
                host.overlayHost.removeView(shade)
                cancel()
            }
            actions.addView(cancel, actionParams(0))
        }

        val scroll = ScrollView(host).apply { addView(actions) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2, 1f))
        shade.addView(panel, host.centerParams(host.dp(330), -2))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    fun openCustomDialog() {
        host.overlayController.showInput(
            host.tr3("Custom time", "Свое время", "◷"),
            host.tr3("Minutes", "Минуты", "′"),
            host.appearanceState.customTimerMinutes.toString(),
            true,
        ) { value ->
            try {
                host.appearanceState.customTimerMinutes = max(1, value.trim().toInt())
                host.saveState()
                start(host.appearanceState.customTimerMinutes)
            } catch (_: Exception) {
            }
        }
    }

    fun start(minutes: Int) {
        host.appearanceState.customTimerMinutes = max(1, minutes)
        host.saveUiState()
        val delayMs = max(1L, minutes.toLong()) * 60L * 1000L
        host.playbackUiState.sleepTimerEndsAt = System.currentTimeMillis() + delayMs
        host.playbackController.startSleepTimer(delayMs)
        host.playerUiController.syncPlaybackUi()
    }

    fun cancel() {
        host.playbackUiState.sleepTimerEndsAt = 0L
        host.playbackController.cancelSleepTimer()
        host.playerUiController.syncPlaybackUi()
    }

    fun buttonText(): String {
        if (
            host.playbackUiState.sleepTimerEndsAt > 0L &&
            host.playbackUiState.sleepTimerEndsAt <= System.currentTimeMillis()
        ) {
            syncFromService()
        }
        if (host.playbackUiState.sleepTimerEndsAt <= 0L) return host.tr("Timer ◷", "Таймер ◷")
        val remainingMs = max(
            0L,
            host.playbackUiState.sleepTimerEndsAt - System.currentTimeMillis(),
        )
        return host.formatSeconds((remainingMs + 999L) / 1000L)
    }

    private fun actionParams(bottomMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, host.dp(50)).apply {
            setMargins(0, host.dp(4), 0, host.dp(bottomMargin))
        }

    private fun syncFromService() {
        host.playbackUiState.sleepTimerEndsAt = PlaybackSleepTimer.readEndsAt(host)
    }
}
