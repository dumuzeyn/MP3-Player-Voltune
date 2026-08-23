package com.dumuzeyn.mp3player;

/** Counts listening time from the service process, independently of Activity connections. */
final class PlaybackHistoryTracker {
    interface Listener {
        void onPlayed(String trackId, boolean completed, long timestamp);
        void onSkipped(String trackId, long timestamp);
    }

    private static final long MAX_SAMPLE_GAP_MS = 15_000L;
    private final Listener listener;
    private String trackId = "";
    private long durationMs;
    private long listenedMs;
    private long lastSampleAt;
    private boolean playing;
    private boolean counted;

    PlaybackHistoryTracker(Listener listener) {
        this.listener = listener;
    }

    void transitionTo(String newTrackId, long newDurationMs, long now, boolean userInitiated) {
        sample(now);
        finish(userInitiated, now);
        trackId = newTrackId == null ? "" : newTrackId;
        durationMs = Math.max(0L, newDurationMs);
        listenedMs = 0L;
        counted = false;
        lastSampleAt = now;
    }

    void setPlaying(boolean value, long now) {
        sample(now);
        playing = value;
        lastSampleAt = now;
    }

    void sample(long now) {
        if (playing && !trackId.isEmpty() && lastSampleAt > 0L) {
            listenedMs += Math.min(MAX_SAMPLE_GAP_MS, Math.max(0L, now - lastSampleAt));
            if (!counted && PlaybackStatisticsPolicy.countsAsPlay(listenedMs, durationMs)) {
                counted = true;
                listener.onPlayed(trackId, false, now);
            }
        }
        lastSampleAt = now;
    }

    void updateDuration(long value) {
        if (value > 0L) {
            durationMs = value;
        }
    }

    void finish(boolean userInitiated, long now) {
        if (trackId.isEmpty()) {
            return;
        }
        if (counted) {
            boolean completed = durationMs > 0L && listenedMs >= durationMs * 9L / 10L;
            if (completed) {
                listener.onPlayed(trackId, true, now);
            }
        } else if (PlaybackStatisticsPolicy.countsAsSkip(
                listenedMs, durationMs, userInitiated)) {
            listener.onSkipped(trackId, now);
        }
        trackId = "";
        playing = false;
    }

    long listenedMs() {
        return listenedMs;
    }
}
