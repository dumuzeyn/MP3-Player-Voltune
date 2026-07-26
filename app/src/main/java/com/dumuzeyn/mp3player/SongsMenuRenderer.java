package com.dumuzeyn.mp3player;

final class SongsMenuRenderer implements MenuRenderer {
    private final MainActivityCore host;

    SongsMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    @Override
    public boolean needsMiniSpacer() {
        return false;
    }

    @Override
    public void render() {
        host.songsRenderer.renderLibrary(
                host.libraryState.tracks, host.navigationState.search);
    }
}
