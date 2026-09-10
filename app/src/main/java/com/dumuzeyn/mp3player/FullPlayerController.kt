package com.dumuzeyn.mp3player

import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Owns the full-screen surface; page-specific UI lives in dedicated controllers. */
internal class FullPlayerController(
    private val host: MainActivityCore,
    private val actions: PlaybackActions,
    private val playbackState: PlaybackStateProvider,
) {
    private var currentSheet: FrameLayout? = null
    private var pager: FullPlayerPagerController? = null
    private var hostVisible = true

    fun open() {
        if (playbackState.currentTrack() == null) return
        if (isOpen()) {
            refresh()
            return
        }
        host.miniPlayer?.visibility = View.GONE
        val sheet = FullPlayerSheet(host) { value -> close(value, true) }
        currentSheet = sheet
        addBackground(sheet)
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), host.dp(10), host.dp(10), host.dp(10))
        }
        sheet.addView(content, host.responsiveLayoutController.fullPlayerContentParams())
        addHeader(content, sheet)
        val createdPager = FullPlayerPagerController(host, actions, playbackState)
        pager = createdPager
        content.addView(createdPager.createPager(), LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(
            createdPager.createIndicator(),
            LinearLayout.LayoutParams(-1, host.dp(18)),
        )
        createdPager.setHostVisible(hostVisible)

        val animate = host.appearanceState.animations && host.navigationState.fullPlayerOpening
        host.navigationState.fullPlayerOpening = false
        host.overlayHost.addView(sheet, FrameLayout.LayoutParams(-1, -1))
        if (animate) {
            sheet.translationY = host.resources.displayMetrics.heightPixels.toFloat()
            sheet.animate()
                .translationY(0f)
                .setDuration(145L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun refresh() {
        if (playbackState.currentTrack() == null) closeExpiredSession() else pager?.refresh()
    }

    fun closeExpiredSession() {
        host.navigationState.fullPlayerOpening = false
        currentSheet?.animate()?.cancel()
        close(currentSheet, false)
    }

    fun onHostVisibilityChanged(visible: Boolean) {
        hostVisible = visible
        pager?.setHostVisible(visible)
    }

    fun isOpen(): Boolean = currentSheet?.parent != null

    fun closeIfTop(top: View): Boolean {
        if (top !== currentSheet || !isOpen()) return false
        close(currentSheet, true)
        return true
    }

    fun onHostDestroyed() {
        releasePager()
        currentSheet = null
    }

    private fun addHeader(content: LinearLayout, sheet: FrameLayout) {
        val row = host.uiFactory.row()
        val back = host.uiFactory.icon("←").apply {
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = host.tr("Close player", "Закрыть плеер")
            setOnClickListener { close(sheet, false) }
        }
        row.addView(back, host.uiFactory.square(54))
        row.addView(View(host), LinearLayout.LayoutParams(0, 1, 1f))
        content.addView(row, LinearLayout.LayoutParams(-1, host.dp(56)))
    }

    private fun addBackground(sheet: FrameLayout) {
        sheet.setBackgroundColor(
            host.appearanceState.playerSolidBackground.takeIf { it != 0 } ?: host.bg,
        )
        when {
            host.appearanceState.playerBackgroundMode ==
                BackgroundSettingsController.MODE_GRADIENT -> {
                val config = object : PlayerGradientBackground.Config {
                    override fun animationsEnabled(): Boolean =
                        hostVisible && host.appearanceState.animations

                    override fun darkTheme(): Boolean = host.appearanceState.dark

                    override fun baseColor(): Int = host.bg
                }
                sheet.addView(
                    PlayerGradientBackground(
                        host,
                        config,
                        host.appearanceState.playerGradientStart,
                        host.appearanceState.playerGradientEnd,
                    ),
                    fill(),
                )
            }

            host.appearanceState.playerBackgroundMode == BackgroundSettingsController.MODE_MEDIA &&
                host.appearanceState.playerBackgroundMediaUri.isNotEmpty() -> {
                sheet.addView(
                    BackgroundMediaView(
                        host,
                        host.appearanceState.playerBackgroundMediaUri,
                        host.appearanceState.playerBackgroundBlur,
                        host.bg,
                    ),
                    fill(),
                )
            }
        }
    }

    private fun fill(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(-1, -1)

    private fun close(sheet: FrameLayout?, animate: Boolean) {
        if (sheet?.parent == null) {
            host.playerUiController.updateMini()
            return
        }
        pager?.setHostVisible(false)
        if (animate && host.appearanceState.animations) {
            sheet.animate()
                .translationY(host.resources.displayMetrics.heightPixels.toFloat())
                .alpha(0f)
                .setDuration(135L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    releasePager()
                    removeSheet(sheet)
                }
                .start()
        } else {
            releasePager()
            removeSheet(sheet)
        }
        if (sheet === currentSheet) currentSheet = null
    }

    private fun removeSheet(sheet: FrameLayout) {
        if (sheet.parent != null) host.overlayHost.removeView(sheet)
        host.playerUiController.updateMini()
    }

    private fun releasePager() {
        pager?.close()
        pager = null
    }
}
