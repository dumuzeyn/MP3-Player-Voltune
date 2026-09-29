package com.dumuzeyn.mp3player

import android.view.View
import android.view.ViewGroup

/** Updates visible and cached artwork without rebuilding the current menu. */
internal object CoverAppearanceRefresher {
    fun refresh(host: MainActivityCore) {
        refreshTree(host.root, host.appearanceState.fullPlayerRotationSpeed)
        host.playerUiController.syncPlaybackUi()
    }

    private fun refreshTree(view: View?, speed: Int) {
        if (view == null) return
        if (view is ShapedCoverImageView) view.invalidateCoverShape()
        if (view is RotatingCoverImageView) {
            view.setRotationSpeedPercent(speed)
            view.refreshCoverTransform()
            view.updatePlaybackState()
        }
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> refreshTree(view.getChildAt(index), speed) }
        }
    }
}
