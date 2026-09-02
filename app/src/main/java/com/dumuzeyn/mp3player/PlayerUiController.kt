package com.dumuzeyn.mp3player

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

internal class PlayerUiController(
    host: MainActivityCore,
    playbackActions: PlaybackActions,
    playbackState: PlaybackStateProvider,
) {
    private val miniPlayerController = MiniPlayerController(host, playbackActions, playbackState)
    private val fullPlayerController = FullPlayerController(host, playbackActions, playbackState)

    fun buildMini(root: FrameLayout) = miniPlayerController.build(root)

    fun openFullPlayer() = fullPlayerController.open()

    fun updateMini() = miniPlayerController.updateState()

    fun syncPlaybackUi() {
        miniPlayerController.updateState()
        if (fullPlayerController.isOpen) fullPlayerController.refresh()
    }

    fun onHostVisibilityChanged(visible: Boolean) =
        fullPlayerController.onHostVisibilityChanged(visible)

    fun isInsideMiniPlayer(event: MotionEvent): Boolean =
        miniPlayerController.isInsideMiniPlayer(event)

    fun closeFullPlayerIfTop(top: View): Boolean = fullPlayerController.closeIfTop(top)

    fun onHostDestroyed() = fullPlayerController.onHostDestroyed()
}
