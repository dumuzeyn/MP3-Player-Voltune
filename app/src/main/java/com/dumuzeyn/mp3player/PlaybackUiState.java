package com.dumuzeyn.mp3player;

import java.util.ArrayList;

/** UI projection of Media3 state. Media3 remains the playback source of truth. */
final class PlaybackUiState {
    final ArrayList<Track> queue = new ArrayList<>();
    private PlaybackSnapshot snapshot = PlaybackSnapshot.empty();
    long sleepTimerEndsAt;

    PlaybackSnapshot snapshot() {
        return snapshot;
    }

    void updateSnapshot(PlaybackSnapshot value) {
        snapshot = value == null ? PlaybackSnapshot.empty() : value;
    }

    int currentTrackIndex(LibraryState library) {
        if (snapshot.currentMediaId.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < library.tracks.size(); index++) {
            if (MediaItemMapper.matchesMediaId(
                    library.tracks.get(index), snapshot.currentMediaId)) {
                return index;
            }
        }
        return -1;
    }

    boolean isPlaying() {
        return snapshot.playWhenReady
                && snapshot.phase != PlaybackPhase.ENDED
                && snapshot.phase != PlaybackPhase.ERROR;
    }

    int repeatMode() {
        return RepeatModeMapper.fromMedia3(snapshot.repeatMode);
    }

    boolean shuffleEnabled() {
        return snapshot.shuffleEnabled;
    }
}
