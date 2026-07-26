package com.dumuzeyn.mp3player;

final class ArtistsMenuRenderer extends TrackGroupMenuRenderer {
    ArtistsMenuRenderer(MainActivityCore host) {
        super(host);
    }

    @Override
    String groupValue(Track track) {
        return track.artist;
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
