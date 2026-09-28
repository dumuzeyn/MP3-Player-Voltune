package com.dumuzeyn.mp3player

import android.view.View
import android.widget.LinearLayout

/** Reserves scroll space only while the mini player is visible. */
object MiniPlayerSpacer {
    private const val TAG = "mini-player-spacer"

    @JvmStatic
    fun addIfNeeded(host: MainActivityCore) {
        val currentIndex = host.currentTrackIndex()
        if (
            currentIndex !in host.libraryState.tracks.indices ||
            host.overlayHost.childCount > 0
        ) {
            return
        }
        host.list.addView(create(host))
    }

    fun sync(host: MainActivityCore, content: LinearLayout) {
        val existing = content.findViewWithTag<View>(TAG)
        if (host.miniPlayer.visibility == View.VISIBLE) {
            if (existing == null) content.addView(create(host))
        } else if (existing != null) {
            content.removeView(existing)
        }
    }

    private fun create(host: MainActivityCore): View = View(host).apply {
        tag = TAG
        layoutParams = LinearLayout.LayoutParams(-1, host.dp(88))
    }
}
