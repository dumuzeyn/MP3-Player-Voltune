package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Map;

final class AlbumsMenuRenderer extends TrackGroupMenuRenderer {
    AlbumsMenuRenderer(MainActivityCore host) {
        super(host);
    }

    @Override
    Map<String, ArrayList<Track>> groupedTracks() {
        return host.libraryState.homeContent.albumTracks;
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
