package com.dumuzeyn.mp3player

class FavoritesMenuRenderer(private val host: MainActivityCore) : MenuRenderer {
    override fun needsMiniSpacer(): Boolean = false

    override fun render() {
        host.renderSongs(
            host.libraryListController.filter(host.libraryListController.favoriteTracks()),
        )
    }
}
