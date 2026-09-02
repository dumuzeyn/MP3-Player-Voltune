package com.dumuzeyn.mp3player

import android.view.View
import android.view.ViewGroup

/** Pauses visual work only; playback remains in the foreground Media3 service. */
object UiVisibilityController {
    @JvmStatic
    fun apply(host: MainActivityCore, visible: Boolean) {
        if (!visible) {
            host.tabsController.cancelScrollAnimation()
            host.swipeController.cancelForBackground()
            host.miniPlayer?.animate()?.cancel()
        }
        host.particleEffectsView?.setUiActive(visible)
        host.songsView?.setHostVisible(visible)
        applyToTree(host.root, visible)
        host.playerUiController.onHostVisibilityChanged(visible)
    }

    private fun applyToTree(view: View?, visible: Boolean) {
        if (view == null) return
        when (view) {
            is BackgroundMediaView -> view.setUiActive(visible)
            is PlayerGradientBackground -> view.setUiActive(visible)
            is RotatingCoverImageView -> view.setUiActive(visible)
        }
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> applyToTree(view.getChildAt(index), visible) }
        }
    }
}
