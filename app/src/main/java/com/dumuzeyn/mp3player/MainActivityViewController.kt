package com.dumuzeyn.mp3player

import android.widget.FrameLayout

/** Builds the main view tree and refreshes playback-dependent chrome. */
internal class MainActivityViewController(
    private val host: MainActivityCore,
    private val screen: MainScreenView,
    private val callbacks: MainScreenCallbacks,
) {
    fun build() {
        host.themeController.applyPalette()
        host.themeController.applyWindow()
        host.refreshTabLabels()
        val appearance = MainScreenView.Appearance(
            if (host.appearanceState.mainSolidBackground == 0) {
                host.bg
            } else {
                host.appearanceState.mainSolidBackground
            },
            host.appearanceState.mainBackgroundMode,
            host.appearanceState.mainGradientStart,
            host.appearanceState.mainGradientEnd,
            host.appearanceState.mainBackgroundMediaUri,
            host.appearanceState.mainBackgroundBlur,
        )
        val views = screen.build(appearance, callbacks)
        host.root = views.root
        host.page = views.page
        host.contentHost = views.contentHost
        host.contentScroll = views.contentScroll
        host.list = views.contentList
        host.songsView?.close()
        host.songsView = SongsView(host)
        host.contentHost.addView(host.songsView, FrameLayout.LayoutParams(-1, -1))
        host.overlayHost = views.overlayHost
        host.setParticleEffectsView(views.particles)
        host.setContentView(host.root)
        host.render()
    }

    fun refreshPlaybackChrome() {
        host.songRows.refresh(stateResolver())
        host.mainRenderer.refreshHomePlayback()
        host.songsView?.refreshPlayback()
        host.playlistController.refreshPlaybackState()
        host.overlayController.refreshPlayback()
        host.sourcePlayButton?.text = if (
            host.playbackQueueController.isPlayingSource(host.currentVisibleTracks())
        ) {
            "Ⅱ"
        } else {
            "▶"
        }
        host.playerUiController.updateMini()
    }

    fun stateResolver(): SongRowStateRegistry.StateResolver =
        object : SongRowStateRegistry.StateResolver {
            override fun currentTrack(): Track? = host.playbackStateProvider.currentTrack()

            override fun isPlaying(): Boolean = host.isPlaybackPlaying

            override fun activeColor(): Int = host.purple

            override fun secondaryActiveColor(): Int = host.yellow

            override fun inactiveColor(): Int = host.purpleSoft
        }
}
