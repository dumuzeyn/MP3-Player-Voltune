package com.dumuzeyn.mp3player;

import android.content.Context;
import android.os.Handler;
import android.widget.ImageView;

/** Owns artwork loading, visible artwork bindings, waveform creation, and memory trimming. */
final class TrackArtworkUi implements AutoCloseable {
    interface Dependencies {
        boolean renderingPreview();

        SongRowStateRegistry activeRows();

        Track findTrack(String uri);

        boolean isCurrent(Track track);

        boolean isPlaying();

        int activeColor();

        int secondaryActiveColor();

        int inactiveColor();

        boolean animationsEnabled();
    }

    private final Context context;
    private final Dependencies dependencies;
    private final CoverLoader coverLoader;

    TrackArtworkUi(Context context, Handler mainHandler, Dependencies dependencies) {
        this.context = context;
        this.dependencies = dependencies;
        this.coverLoader = new CoverLoader(context, mainHandler);
    }

    void loadCover(ImageView view, Track track, int fallbackColor) {
        loadCover(view, track, fallbackColor, CoverLoader.THUMB_SIZE);
    }

    void loadCover(ImageView view, Track track, int fallbackColor, int maxSize) {
        registerCover(view, track);
        if (dependencies.renderingPreview()) {
            coverLoader.loadCachedOnly(view, track, fallbackColor, maxSize);
        } else {
            coverLoader.load(view, track, fallbackColor, maxSize);
        }
    }

    void loadCoverSmooth(ImageView view, Track track, int fallbackColor) {
        registerCover(view, track);
        if (dependencies.renderingPreview()) {
            coverLoader.loadCachedOnly(view, track, fallbackColor, CoverLoader.THUMB_SIZE);
        } else {
            coverLoader.loadSmooth(view, track, fallbackColor, CoverLoader.THUMB_SIZE,
                    dependencies.animationsEnabled() ? 320 : 0);
        }
    }

    void loadUnregisteredCover(ImageView view, Track track, int fallbackColor, int maxSize) {
        if (view instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) view).bindTrack(track);
        }
        coverLoader.load(view, track, fallbackColor, maxSize);
    }

    void promoteVisibleArtwork() {
        dependencies.activeRows().forEachCover((uri, cover) -> {
            Track track = dependencies.findTrack(uri);
            if (track != null) {
                coverLoader.loadSmooth(cover, track, dependencies.inactiveColor(),
                        CoverLoader.THUMB_SIZE,
                        dependencies.animationsEnabled() ? 180 : 0);
            }
        });
    }

    void seedFromView(ImageView view, Track track) {
        coverLoader.seedFromView(view, track);
    }

    void clearCover(ImageView view, int fallbackColor) {
        coverLoader.clear(view, fallbackColor);
        if (view instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) view).bindTrack(null);
        }
    }

    WaveformView createWaveform(Track track, boolean active) {
        WaveformView waveform = new WaveformView(context, track.title + track.uri,
                active ? dependencies.activeColor() : dependencies.inactiveColor(),
                dependencies.secondaryActiveColor(),
                active && dependencies.isPlaying());
        int minimumHeight = dp(28);
        int verticalPadding = dp(3);
        waveform.setMinimumHeight(minimumHeight);
        waveform.setPadding(0, verticalPadding, 0, verticalPadding);
        waveform.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                dp(190), dp(30)));
        return waveform;
    }

    void onTrimMemory(int level) {
        coverLoader.trimMemory(level);
    }

    @Override
    public void close() {
        coverLoader.close();
    }

    private void registerCover(ImageView view, Track track) {
        if (!(view instanceof RotatingCoverImageView)) {
            return;
        }
        RotatingCoverImageView cover = (RotatingCoverImageView) view;
        cover.bindTrack(track);
        if (track != null) {
            dependencies.activeRows().registerCover(track.uri, cover);
        }
    }

    private int dp(int value) {
        return Math.max(1, Math.round(
                value * context.getResources().getDisplayMetrics().density));
    }
}
