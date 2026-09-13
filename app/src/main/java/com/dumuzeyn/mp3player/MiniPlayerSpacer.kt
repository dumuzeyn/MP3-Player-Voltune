package com.dumuzeyn.mp3player

import android.view.View
import android.widget.LinearLayout

/** Reserves scroll space only while the mini player is visible. */
object MiniPlayerSpacer {
    @JvmStatic
    fun addIfNeeded(host: MainActivityCore) {
        val currentIndex = host.currentTrackIndex()
        if (
            currentIndex !in host.libraryState.tracks.indices ||
            host.overlayHost.childCount > 0
        ) {
            return
        }
        val spacer = View(host).apply {
            layoutParams = LinearLayout.LayoutParams(-1, host.dp(88))
        }
        host.list.addView(spacer)
    }
}
