package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Map;

final class ArtistsMenuRenderer extends TrackGroupMenuRenderer {
    ArtistsMenuRenderer(MainActivityCore host) {
        super(host);
    }

    @Override
    Map<String, ArrayList<Track>> groupedTracks() {
        return host.libraryState.homeContent.artistTracks;
    }

    @Override
    String unknownGroupName() {
        return host.tr("Unknown artist", "Неизвестный исполнитель");
    }

    @Override
    int cardOpacity() {
        return host.appearanceState.artistCardOpacity;
    }
}
