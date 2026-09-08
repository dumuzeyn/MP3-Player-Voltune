package com.dumuzeyn.mp3player

import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.max

internal class MiniPlayerController(
    private val host: MainActivityCore,
    private val playbackActions: PlaybackActions,
    private val playbackState: PlaybackStateProvider,
) {
    private var draggingMiniPlayer = false

    fun build(root: FrameLayout) {
        host.miniPlayer = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(14), 0, host.dp(10), 0)
            visibility = View.GONE
            setOnClickListener(::openFullPlayerFromMini)
            setOnTouchListener(MiniSwipeListener())
        }
        host.uiFactory.applyCardStyle(
            host.miniPlayer,
            host.appearanceState.miniPlayerCardOpacity,
        )

        val textColumn = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        host.miniTitle = host.uiFactory.text(host.tr("Song", "Песня"), 16, true).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        host.miniSub = host.uiFactory.text(
            host.tr("Unknown artist", "Неизвестный исполнитель"),
            12,
            false,
        ).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(host.miniTitle)
        textColumn.addView(host.miniSub)
        host.miniPlayer.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

        host.miniButton = host.uiFactory.icon("▶")
        host.uiFactory.applyPrimaryButtonStyle(host.miniButton)
        host.miniButton.setOnClickListener { playbackActions.togglePlayPause() }
        host.miniPlayer.addView(host.miniButton, host.uiFactory.square(52))

        root.addView(host.miniPlayer, host.responsiveLayoutController.miniPlayerParams())
    }

    fun updateState() {
        if (!hasMiniPlayer()) return
        val track = currentTrack()
        if (track == null || isOverlayOpen()) {
            hideMiniPlayer()
            return
        }
        bindMiniPlayer(track)
        showMiniPlayer()
    }

    fun isInsideMiniPlayer(event: MotionEvent): Boolean {
        if (!hasMiniPlayer() || host.miniPlayer.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        host.miniPlayer.getLocationOnScreen(location)
        val rawX = event.rawX
        val rawY = event.rawY
        return rawX >= location[0] && rawX <= location[0] + host.miniPlayer.width &&
            rawY >= location[1] && rawY <= location[1] + host.miniPlayer.height
    }

    private fun openFullPlayerFromMini(view: View) {
        if (host.appearanceState.animations) {
            view.animate()
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(35L)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(60L).start()
                    host.navigationState.fullPlayerOpening = true
                    host.playerUiController.openFullPlayer()
                }
                .start()
        } else {
            host.navigationState.fullPlayerOpening = true
            host.playerUiController.openFullPlayer()
        }
    }

    private fun hasMiniPlayer(): Boolean = host.miniPlayer != null

    private fun currentTrack(): Track? = playbackState.currentTrack()

    private fun isOverlayOpen(): Boolean = host.overlayHost.childCount > 0

    private fun bindMiniPlayer(track: Track) {
        host.miniTitle.text = track.title
        host.miniSub.text = track.artist
        host.miniButton.text = if (playbackState.isPlaying()) "Ⅱ" else "▶"
    }

    private fun hideMiniPlayer() {
        host.miniPlayer.visibility = View.GONE
    }

    private fun showMiniPlayer() {
        if (draggingMiniPlayer) {
            host.miniPlayer.visibility = View.VISIBLE
            return
        }
        host.miniPlayer.translationX = 0f
        host.miniPlayer.alpha = 1f
        host.miniPlayer.visibility = View.VISIBLE
    }

    private inner class MiniSwipeListener : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    dragging = false
                    view.animate().cancel()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!dragging && abs(dx) > host.dp(16) && abs(dx) > abs(dy) * 1.4f) {
                        dragging = true
                        draggingMiniPlayer = true
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        host.miniPlayer.translationX = dx
                        host.miniPlayer.alpha = max(
                            0.35f,
                            1f - abs(dx) / max(1, host.miniPlayer.width),
                        )
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) {
                        view.performClick()
                        return true
                    }
                    val dx = event.rawX - startX
                    dragging = false
                    if (abs(dx) >= max(host.dp(96).toFloat(), view.width * 0.28f)) {
                        dismissMiniPlayer(dx)
                    } else {
                        draggingMiniPlayer = false
                        host.miniPlayer.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(if (host.appearanceState.animations) 120L else 0L)
                            .start()
                    }
                    return true
                }
                else -> return false
            }
        }
    }

    private fun dismissMiniPlayer(dx: Float) {
        val target = if (dx < 0f) -host.miniPlayer.width.toFloat() else host.miniPlayer.width.toFloat()
        if (host.appearanceState.animations) {
            host.miniPlayer.animate()
                .translationX(target)
                .alpha(0f)
                .setDuration(130L)
                .withEndAction {
                    draggingMiniPlayer = false
                    host.stopPlaybackAndClearQueue()
                    host.miniPlayer.translationX = 0f
                    host.miniPlayer.alpha = 1f
                }
                .start()
        } else {
            draggingMiniPlayer = false
            host.stopPlaybackAndClearQueue()
            host.miniPlayer.translationX = 0f
            host.miniPlayer.alpha = 1f
        }
    }
}
