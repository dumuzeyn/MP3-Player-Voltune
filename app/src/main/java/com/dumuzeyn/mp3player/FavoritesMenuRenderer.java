package com.dumuzeyn.mp3player;

final class FavoritesMenuRenderer implements MenuRenderer {
    private final MainActivityCore host;

    FavoritesMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    void loadFavorites() {
        LibraryDatabase database = new LibraryDatabase(host);
        try {
            host.libraryState.favorites.clear();
            host.libraryState.favorites.addAll(database.loadFavorites());
        } finally {
            database.close();
        }
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
