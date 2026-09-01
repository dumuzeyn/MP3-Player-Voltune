package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;

/** Routes UI commands to the existing Media3 playback components. */
final class Media3PlaybackActions implements PlaybackActions {
    private final PlaybackQueueController queue;
    private final PlaybackController playback;

    Media3PlaybackActions(PlaybackQueueController queue, PlaybackController playback) {
        this.queue = queue;
        this.playback = playback;
    }

    @Override
    public void playTrack(Track track) {
        queue.playTrack(track);
    }

    @Override
    public void playTracks(List<Track> tracks, boolean shuffle) {
        queue.playList(new ArrayList<>(tracks), shuffle);
    }

    @Override
    public void togglePlayPause() {
        queue.toggleOrStart();
    }

    @Override
    public void next() {
        playback.next();
    }

    @Override
    public void previous() {
        playback.previous();
    }

    @Override
    public void seekTo(long positionMs) {
        playback.seekTo((int) Math.min(Integer.MAX_VALUE, Math.max(0L, positionMs)));
    }

    @Override
    public void cycleRepeatMode() {
        playback.cycleRepeatMode();
    }
}
