package com.dumuzeyn.mp3player;

final class AlbumsMenuRenderer extends TrackGroupMenuRenderer {
    AlbumsMenuRenderer(MainActivityCore host) {
        super(host);
    }

    @Override
    String groupValue(Track track) {
        return track.album;
    }

    @Override
    String unknownGroupName() {
        return host.tr("Unknown album", "Неизвестный альбом");
    }

    @Override
    int cardOpacity() {
        return host.appearanceState.albumCardOpacity;
    }
}
