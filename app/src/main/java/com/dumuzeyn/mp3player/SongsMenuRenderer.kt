package com.dumuzeyn.mp3player

class SongsMenuRenderer(private val host: MainActivityCore) : MenuRenderer {
    override fun needsMiniSpacer(): Boolean = false

    override fun render() {
        host.songsRenderer.renderLibrary(
            host.libraryState.tracks,
            host.navigationState.search,
        )
    }
}
