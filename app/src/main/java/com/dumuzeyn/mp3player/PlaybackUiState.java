package com.dumuzeyn.mp3player;

import java.util.ArrayList;

/** UI projection of Media3 state. Media3 remains the playback source of truth. */
final class PlaybackUiState {
    final ArrayList<Track> queue = new ArrayList<>();
    private PlaybackSnapshot snapshot = PlaybackSnapshot.empty();
    private String cachedMediaId = "";
    private int cachedTrackIndex = -1;
    long sleepTimerEndsAt;

    PlaybackSnapshot snapshot() {
        return snapshot;
    }

    void updateSnapshot(PlaybackSnapshot value) {
        PlaybackSnapshot next = value == null ? PlaybackSnapshot.empty() : value;
        if (!snapshot.currentMediaId.equals(next.currentMediaId)) {
            cachedMediaId = "";
            cachedTrackIndex = -1;
        }
        snapshot = next;
    }

    int currentTrackIndex(LibraryState library) {
        if (snapshot.currentMediaId.isEmpty()) {
            return -1;
        }
        if (cachedMediaId.equals(snapshot.currentMediaId)
                && cachedTrackIndex >= 0 && cachedTrackIndex < library.tracks.size()
                && MediaItemMapper.matchesMediaId(
                        library.tracks.get(cachedTrackIndex), snapshot.currentMediaId)) {
            return cachedTrackIndex;
        }
        for (int index = 0; index < library.tracks.size(); index++) {
            if (MediaItemMapper.matchesMediaId(
                    library.tracks.get(index), snapshot.currentMediaId)) {
                cachedMediaId = snapshot.currentMediaId;
                cachedTrackIndex = index;
                return index;
            }
        }
        cachedMediaId = snapshot.currentMediaId;
        cachedTrackIndex = -1;
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
