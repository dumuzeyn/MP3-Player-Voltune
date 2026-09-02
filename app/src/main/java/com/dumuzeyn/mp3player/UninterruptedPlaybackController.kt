package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences

class UninterruptedPlaybackController(private val host: MainActivityCore) {
    fun settingLabel(): String = host.tr(
        "Always play: ",
        "Всегда играть: ",
    ) + host.tr(if (enabled()) "on" else "off", if (enabled()) "вкл" else "выкл")

    fun toggle() {
        prefs().edit().putBoolean(ENABLED, !enabled()).apply()
        host.render()
    }

    private fun enabled(): Boolean = prefs().getBoolean(ENABLED, false)

    private fun prefs(): SharedPreferences =
        host.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val PREFS = "playback_behavior"
        const val ENABLED = "uninterrupted_playback"
    }
}
