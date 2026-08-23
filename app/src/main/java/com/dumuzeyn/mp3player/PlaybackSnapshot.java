package com.dumuzeyn.mp3player;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaybackSnapshot {
    public final List<String> queueMediaIds;
    public final String currentMediaId;
    public final int currentIndex;
    public final long positionMs;
    public final long durationMs;
    public final boolean playWhenReady;
    public final int playbackState;
    public final int repeatMode;
    public final boolean shuffleEnabled;
    public final PlaybackPhase phase;
    public final PauseReason pauseReason;
    public final StopReason stopReason;
    @Nullable public final PlaybackErrorInfo lastError;
    public final long updatedAt;

    public PlaybackSnapshot(List<String> queueMediaIds, @Nullable String currentMediaId,
            int currentIndex, long positionMs, long durationMs, boolean playWhenReady,
            int playbackState, int repeatMode, boolean shuffleEnabled, PlaybackPhase phase,
            PauseReason pauseReason, StopReason stopReason,
            @Nullable PlaybackErrorInfo lastError, long updatedAt) {
        this.queueMediaIds = Collections.unmodifiableList(new ArrayList<>(queueMediaIds));
        this.currentMediaId = currentMediaId == null ? "" : currentMediaId;
        this.currentIndex = currentIndex;
        this.positionMs = Math.max(0L, positionMs);
        this.durationMs = Math.max(0L, durationMs);
        this.playWhenReady = playWhenReady;
        this.playbackState = playbackState;
        this.repeatMode = repeatMode;
        this.shuffleEnabled = shuffleEnabled;
        this.phase = phase == null ? PlaybackPhase.IDLE : phase;
        this.pauseReason = pauseReason == null ? PauseReason.NONE : pauseReason;
        this.stopReason = stopReason == null ? StopReason.NONE : stopReason;
        this.lastError = lastError;
        this.updatedAt = updatedAt;
    }

    public static PlaybackSnapshot empty() {
        return new PlaybackSnapshot(Collections.emptyList(), "", -1, 0L, 0L, false,
                1, 0, false, PlaybackPhase.IDLE, PauseReason.NONE, StopReason.NONE,
                null, System.currentTimeMillis());
    }
}
