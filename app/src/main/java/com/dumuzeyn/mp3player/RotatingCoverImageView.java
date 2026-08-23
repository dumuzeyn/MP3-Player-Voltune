package com.dumuzeyn.mp3player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.HashSet;

final class RotatingCoverImageView extends ImageView {
    private static final long DEFAULT_ROTATION_DURATION_MS = 18000L;

    private final MainActivityCore host;
    private final HashSet<String> trackUris = new HashSet<>();
    private ArrayList<Track> sourceTracks = new ArrayList<>();
    private boolean requireActiveQueue;
    private ValueAnimator rotationAnimator;
    private String boundTrackUri = "";
    private String lastObservedTrackUri = "";
    private boolean seeking;
    private int seekStartPosition;
    private float seekStartRotation;
    private long rotationDurationMs = DEFAULT_ROTATION_DURATION_MS;
    private boolean uiActive = true;

    RotatingCoverImageView(MainActivityCore host) {
        super(host);
        this.host = host;
        setScaleType(ScaleType.CENTER_CROP);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float radius = host.appearanceState.circularCovers
                        ? Math.min(view.getWidth(), view.getHeight()) * 0.5f
                        : host.dp(8);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    void bindTrack(Track track) {
        String nextTrackUri = track == null || track.uri == null ? "" : track.uri;
        if (!nextTrackUri.equals(this.boundTrackUri)) {
            seeking = false;
            stopRotation(true);
            this.boundTrackUri = nextTrackUri;
        }
        this.trackUris.clear();
        this.sourceTracks.clear();
        this.requireActiveQueue = false;
        if (!nextTrackUri.isEmpty()) {
            this.trackUris.add(nextTrackUri);
        }
        updatePlaybackState();
    }

    void setRotationSpeedPercent(int speedPercent) {
        int boundedSpeed = Math.max(25, Math.min(200, speedPercent));
        long nextDuration = Math.round(DEFAULT_ROTATION_DURATION_MS * (100.0 / boundedSpeed));
        if (nextDuration == this.rotationDurationMs) {
            return;
        }
        boolean restart = this.rotationAnimator != null && this.rotationAnimator.isRunning()
                && !this.seeking;
        this.rotationDurationMs = nextDuration;
        if (restart) {
            stopRotation(false);
            startRotation();
        }
    }

    void setUiActive(boolean active) {
        uiActive = active;
        updatePlaybackState();
    }

    void bindTracks(ArrayList<Track> tracks) {
        bindSourceTracks(tracks, false);
    }

    void bindPlaylistTracks(ArrayList<Track> tracks) {
        bindSourceTracks(tracks, true);
    }

    private void bindSourceTracks(ArrayList<Track> tracks, boolean requireActiveQueue) {
        this.trackUris.clear();
        this.sourceTracks = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
        this.requireActiveQueue = requireActiveQueue;
        if (tracks != null) {
            for (Track track : tracks) {
                if (track != null && track.uri != null) {
                    this.trackUris.add(track.uri);
                }
            }
        }
        updatePlaybackState();
    }

    void updatePlaybackState() {
        invalidateOutline();
        String currentUri = "";
        int currentIndex = host.currentTrackIndex();
        if (currentIndex >= 0 && currentIndex < host.libraryState.tracks.size()
                && host.libraryState.tracks.get(currentIndex) != null
                && host.libraryState.tracks.get(currentIndex).uri != null) {
            currentUri = host.libraryState.tracks.get(currentIndex).uri;
        }
        if (!currentUri.isEmpty() && !currentUri.equals(this.lastObservedTrackUri)) {
            if (!this.lastObservedTrackUri.isEmpty()) {
                stopRotation(true);
            }
            this.lastObservedTrackUri = currentUri;
        }
        boolean shouldRotate = uiActive && host.appearanceState.circularCovers
                && host.isPlaybackPlaying()
                && currentIndex >= 0 && currentIndex < host.libraryState.tracks.size()
                && trackUris.contains(host.libraryState.tracks.get(currentIndex).uri)
                && (!requireActiveQueue || host.playbackQueueController.isCurrentCollection(sourceTracks));
        if (seeking) {
            return;
        }
        if (shouldRotate && isAttachedToWindow()) {
            startRotation();
        } else {
            stopRotation(!host.appearanceState.circularCovers);
        }
    }

    void beginSeekSpin(int positionMs) {
        if (!host.appearanceState.circularCovers) {
            return;
        }
        seeking = true;
        seekStartPosition = positionMs;
        stopRotation(false);
        seekStartRotation = getRotation();
    }

    void updateSeekSpin(int positionMs) {
        if (!seeking || !host.appearanceState.circularCovers) {
            return;
        }
        int deltaMs = positionMs - seekStartPosition;
        if (deltaMs == 0) {
            setRotation(seekStartRotation);
            return;
        }
        setRotation(seekStartRotation + degreesForSeekDelta(deltaMs));
    }

    void endSeekSpin(int positionMs, boolean animateTap) {
        if (!seeking) {
            return;
        }
        seeking = false;
        if (!host.appearanceState.circularCovers) {
            updatePlaybackState();
            return;
        }
        int totalDeltaMs = positionMs - seekStartPosition;
        if (totalDeltaMs == 0) {
            setRotation(seekStartRotation);
            updatePlaybackState();
            return;
        }
        float target = seekStartRotation + degreesForSeekDelta(totalDeltaMs);
        if (!animateTap || !host.appearanceState.animations) {
            setRotation(target % 360.0f);
            updatePlaybackState();
            return;
        }
        setRotation(seekStartRotation);
        ValueAnimator animator = ValueAnimator.ofFloat(seekStartRotation, target);
        rotationAnimator = animator;
        float turns = Math.abs(target - seekStartRotation) / 360.0f;
        animator.setDuration((long) clamp(180.0f + turns * 115.0f, 220.0f, 850.0f));
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> setRotation((Float) value.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (rotationAnimator != animator) {
                    return;
                }
                rotationAnimator = null;
                setRotation(getRotation() % 360.0f);
                updatePlaybackState();
            }
        });
        animator.start();
    }

    private float degreesForSeekDelta(int deltaMs) {
        return deltaMs * (360.0f / this.rotationDurationMs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePlaybackState();
    }

    @Override
    protected void onDetachedFromWindow() {
        seeking = false;
        stopRotation(true);
        super.onDetachedFromWindow();
    }

    private void startRotation() {
        if (this.rotationAnimator != null && this.rotationAnimator.isRunning()) {
            return;
        }
        float start = getRotation() % 360.0f;
        this.rotationAnimator = ValueAnimator.ofFloat(start, start + 360.0f);
        this.rotationAnimator.setDuration(this.rotationDurationMs);
        this.rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        this.rotationAnimator.setInterpolator(new LinearInterpolator());
        this.rotationAnimator.addUpdateListener(animator -> setRotation((Float) animator.getAnimatedValue()));
        this.rotationAnimator.start();
    }

    private void stopRotation(boolean reset) {
        ValueAnimator animator = this.rotationAnimator;
        this.rotationAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
        if (reset) {
            setRotation(0.0f);
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
