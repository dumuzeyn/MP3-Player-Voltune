package com.dumuzeyn.mp3player;

/** Narrow functional binding used by artwork UI without exposing the Activity. */
final class TrackArtworkDependencies implements TrackArtworkUi.Dependencies {
    private final BooleanValueProvider preview;
    private final ValueProvider<SongRowStateRegistry> rows;
    private final TrackFinder trackFinder;
    private final TrackPredicate currentTrack;
    private final BooleanValueProvider playing;
    private final IntValueProvider activeColor;
    private final IntValueProvider secondaryColor;
    private final IntValueProvider inactiveColor;
    private final BooleanValueProvider animations;

    TrackArtworkDependencies(BooleanValueProvider preview,
            ValueProvider<SongRowStateRegistry> rows,
            TrackFinder trackFinder,
            TrackPredicate currentTrack,
            BooleanValueProvider playing,
            IntValueProvider activeColor,
            IntValueProvider secondaryColor,
            IntValueProvider inactiveColor,
            BooleanValueProvider animations) {
        this.preview = preview;
        this.rows = rows;
        this.trackFinder = trackFinder;
        this.currentTrack = currentTrack;
        this.playing = playing;
        this.activeColor = activeColor;
        this.secondaryColor = secondaryColor;
        this.inactiveColor = inactiveColor;
        this.animations = animations;
    }

    @Override
    public boolean renderingPreview() {
        return preview.get();
    }

    @Override
    public SongRowStateRegistry activeRows() {
        return rows.get();
    }

    @Override
    public Track findTrack(String uri) {
        return trackFinder.find(uri);
    }

    @Override
    public boolean isCurrent(Track track) {
        return currentTrack.test(track);
    }

    @Override
    public boolean isPlaying() {
        return playing.get();
    }

    @Override
    public int activeColor() {
        return activeColor.get();
    }

    @Override
    public int secondaryActiveColor() {
        return secondaryColor.get();
    }

    @Override
    public int inactiveColor() {
        return inactiveColor.get();
    }

    @Override
    public boolean animationsEnabled() {
        return animations.get();
    }
}
