package com.dumuzeyn.mp3player;

import androidx.annotation.Nullable;
import java.util.List;

/** Read-only Media3 projection used by player UI. */
interface PlaybackStateProvider {
    PlaybackSnapshot currentSnapshot();

    @Nullable
    Track currentTrack();

    boolean isCurrentTrack(Track track);

    boolean isPlaying();

    int repeatMode();

    List<Track> activeQueue();

    int queueIndex(Track track);
}
