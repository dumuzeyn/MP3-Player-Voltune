package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Map;

final class GenresMenuRenderer extends TrackGroupMenuRenderer {
    GenresMenuRenderer(MainActivityCore host) {
        super(host);
    }

    @Override
    Map<String, ArrayList<Track>> groupedTracks() {
        return host.libraryState.homeContent.genreTracks;
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
