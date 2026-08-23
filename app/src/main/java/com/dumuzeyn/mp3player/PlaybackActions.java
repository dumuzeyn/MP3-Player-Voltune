package com.dumuzeyn.mp3player;

import java.util.List;

/** Commands exposed to playback UI without exposing MediaController internals. */
interface PlaybackActions {
    void playTrack(Track track);

    void playTracks(List<Track> tracks, boolean shuffle);

    void togglePlayPause();

    void next();

    void previous();

    void seekTo(long positionMs);

    void cycleRepeatMode();
}
