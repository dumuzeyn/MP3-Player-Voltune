package com.dumuzeyn.mp3player;

final class PlaybackStatisticsPolicy {
    static final long LONG_TRACK_THRESHOLD_MS = 30_000L;
    static final long SKIP_THRESHOLD_MS = 10_000L;

    private PlaybackStatisticsPolicy() {
    }

    static boolean countsAsPlay(long listenedMs, long durationMs) {
        if (listenedMs <= 0L) {
            return false;
        }
        long required = durationMs > 0L && durationMs < 60_000L
                ? Math.max(5_000L, durationMs / 2L)
                : LONG_TRACK_THRESHOLD_MS;
        return listenedMs >= required;
    }

    static boolean countsAsSkip(long listenedMs, long durationMs, boolean userInitiated) {
        return userInitiated && listenedMs >= 0L && listenedMs < SKIP_THRESHOLD_MS
                && (durationMs <= 0L || listenedMs < durationMs / 2L);
    }
}
