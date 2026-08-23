package com.dumuzeyn.mp3player;

final class FavoritesMenuRenderer implements MenuRenderer {
    private final MainActivityCore host;

    FavoritesMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    @Override
    public boolean needsMiniSpacer() {
        return false;
    }

    @Override
    public void render() {
        host.renderSongs(host.libraryListController.filter(host.libraryListController.favoriteTracks()));
    }
}
