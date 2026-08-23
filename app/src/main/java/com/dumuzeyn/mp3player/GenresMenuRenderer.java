package com.dumuzeyn.mp3player;

final class GenresMenuRenderer extends TrackGroupMenuRenderer {
    GenresMenuRenderer(MainActivityCore host) {
        super(host);
    }

    @Override
    String groupValue(Track track) {
        return track.genre;
    }

    @Override
    String unknownGroupName() {
        return host.tr("Unknown genre", "Неизвестный жанр");
    }

    @Override
    int cardOpacity() {
        return host.appearanceState.genreCardOpacity;
    }
}
