package com.dumuzeyn.mp3player;

import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;

final class FrameLayoutCover extends FrameLayout {
    private final MainActivityCore host;
    private final ImageView front;
    private final ImageView back;
    private boolean frontVisible = true;
    private String currentUri = "";
    private int fallback;
    private ArrayList<Track> sourceTracks = new ArrayList<>();

    FrameLayoutCover(MainActivityCore host) {
        super(host);
        this.host = host;
        this.front = host.uiFactory.coverView();
        this.back = host.uiFactory.coverView();
        this.back.setAlpha(0.0f);
        addView(this.front, new FrameLayout.LayoutParams(-1, -1));
        addView(this.back, new FrameLayout.LayoutParams(-1, -1));
    }

    void setFallback(int fallback) {
        this.fallback = fallback;
        this.front.setBackgroundColor(fallback);
        this.back.setBackgroundColor(fallback);
    }

    void bindPlaylistTracks(ArrayList<Track> tracks) {
        this.sourceTracks = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
        applyPlaylistTracks(this.front);
        applyPlaylistTracks(this.back);
    }

    void bindTrack(Track track, int generation) {
        if (track == null || track.uri.equals(this.currentUri)) {
            return;
        }
        if (this.currentUri.isEmpty()) {
            this.currentUri = track.uri;
            this.frontVisible = true;
            this.front.setAlpha(1.0f);
            this.back.setAlpha(0.0f);
            host.artworkUi.loadCover(this.front, track, this.fallback);
            applyPlaylistTracks(this.front);
            return;
        }
        this.currentUri = track.uri;
        ImageView visible = this.frontVisible ? this.front : this.back;
        ImageView incoming = this.frontVisible ? this.back : this.front;
        incoming.animate().cancel();
        visible.animate().cancel();
        incoming.setAlpha(0.0f);
        host.artworkUi.loadCover(incoming, track, this.fallback);
        applyPlaylistTracks(incoming);
        incoming.animate().alpha(1.0f).setDuration(host.appearanceState.animations ? 520L : 0L).withEndAction(new Runnable() {
            @Override
            public void run() {
                frontVisible = !frontVisible;
            }
        }).start();
        visible.animate().alpha(0.0f).setDuration(host.appearanceState.animations ? 520L : 0L).start();
    }

    private void applyPlaylistTracks(ImageView cover) {
        if (cover instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) cover).bindPlaylistTracks(this.sourceTracks);
        }
    }
}
