package com.dumuzeyn.mp3player;

final class SongsMenuRenderer implements MenuRenderer {
    private final MainActivityCore host;

    SongsMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    void loadSongs() {
        host.libraryState.tracks.clear();
        host.libraryState.tracks.addAll(TrackStore.load(host));
    }

    @Override
    public boolean needsMiniSpacer() {
        return false;
    }

    @Override
    public void render() {
        host.renderSongs(host.libraryListController.filter(host.libraryState.tracks));
    }
}
