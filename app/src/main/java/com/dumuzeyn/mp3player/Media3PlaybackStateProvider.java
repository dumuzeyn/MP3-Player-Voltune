package com.dumuzeyn.mp3player;

import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/** Reads the activity's projection of the authoritative Media3 state. */
final class Media3PlaybackStateProvider implements PlaybackStateProvider {
    private final LibraryState library;
    private final PlaybackUiState playback;

    Media3PlaybackStateProvider(LibraryState library, PlaybackUiState playback) {
        this.library = library;
        this.playback = playback;
    }

    @Override
    public PlaybackSnapshot currentSnapshot() {
        return playback.snapshot();
    }

    @Nullable
    @Override
    public Track currentTrack() {
        int index = playback.currentTrackIndex(library);
        return index < 0 || index >= library.tracks.size()
                ? null : library.tracks.get(index);
    }

    @Override
    public boolean isCurrentTrack(Track track) {
        Track current = currentTrack();
        return current != null && track != null && current.uri.equals(track.uri);
    }

    @Override
    public boolean isPlaying() {
        return playback.isPlaying();
    }

    @Override
    public int repeatMode() {
        return playback.repeatMode();
    }

    @Override
    public List<Track> activeQueue() {
        return Collections.unmodifiableList(playback.queue);
    }

    @Override
    public int queueIndex(Track track) {
        return playback.queue.indexOf(track);
    }
}
