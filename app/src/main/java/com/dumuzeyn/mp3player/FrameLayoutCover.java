package com.dumuzeyn.mp3player;

import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;

final class FrameLayoutCover extends FrameLayout {
    private final MainActivityCore host;
    private final ImageView cover;
    private String currentUri = "";
    private int fallback;
    private ArrayList<Track> sourceTracks = new ArrayList<>();

    FrameLayoutCover(MainActivityCore host) {
        super(host);
        this.host = host;
        this.cover = host.uiFactory.coverView();
        addView(this.cover, new FrameLayout.LayoutParams(-1, -1));
    }

    void setFallback(int fallback) {
        this.fallback = fallback;
        setBackgroundColor(fallback);
        this.cover.setBackgroundColor(fallback);
    }

    void bindPlaylistTracks(ArrayList<Track> tracks) {
        this.sourceTracks = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
        applyPlaylistTracks(this.cover);
    }

    void bindTrack(Track track, int generation) {
        if (track == null || track.uri.equals(this.currentUri)) {
            return;
        }
        this.currentUri = track.uri;
        host.artworkUi.loadCoverSmooth(this.cover, track, this.fallback);
        applyPlaylistTracks(this.cover);
    }

    private void applyPlaylistTracks(ImageView cover) {
        if (cover instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) cover).bindPlaylistTracks(this.sourceTracks);
        }
    }
}
