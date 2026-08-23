package com.dumuzeyn.mp3player;

import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/** Runs the visible player clock and stops immediately off-page or in background. */
final class FullPlayerProgressController implements AutoCloseable {
    private final MainActivityCore host;
    private final PlaybackStateProvider playbackState;
    private final Runnable trackChanged;
    private final Runnable ticker = this::tick;
    private View root;
    private SeekBar seek;
    private TextView elapsed;
    private TextView remaining;
    private String trackUri = "";
    private boolean active;
    private boolean seekTracking;

    FullPlayerProgressController(MainActivityCore host,
            PlaybackStateProvider playbackState, Runnable trackChanged) {
        this.host = host;
        this.playbackState = playbackState;
        this.trackChanged = trackChanged;
    }

    void bind(View root, Track track, SeekBar seek, TextView elapsed, TextView remaining) {
        this.root = root;
        this.trackUri = track == null ? "" : track.uri;
        this.seek = seek;
        this.elapsed = elapsed;
        this.remaining = remaining;
        updateClock();
    }

    void setSeekTracking(boolean tracking) {
        seekTracking = tracking;
    }

    void setActive(boolean value) {
        active = value;
        host.uiHandler.removeCallbacks(ticker);
        if (active) {
            updateClock();
            host.uiHandler.postDelayed(ticker, 250L);
        }
    }

    private void tick() {
        if (!active || root == null || root.getParent() == null) {
            return;
        }
        Track current = playbackState.currentTrack();
        if (current == null) {
            return;
        }
        if (!trackUri.equals(current.uri)) {
            trackUri = current.uri;
            trackChanged.run();
        }
        updateClock();
        host.uiHandler.postDelayed(ticker, 250L);
    }

    private void updateClock() {
        Track track = playbackState.currentTrack();
        if (track == null || seek == null || seekTracking) {
            return;
        }
        int duration = host.playbackDurationFor(track);
        int position = Math.max(0, host.playbackPosition());
        seek.setMax(Math.max(1, duration));
        seek.setProgress(position);
        elapsed.setText(host.formatMs(position));
        remaining.setText("-" + host.formatMs(Math.max(0, duration - position)));
    }

    @Override public void close() {
        active = false;
        host.uiHandler.removeCallbacks(ticker);
        root = null;
        seek = null;
        elapsed = null;
        remaining = null;
    }
}
