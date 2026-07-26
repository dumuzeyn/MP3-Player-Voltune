package com.dumuzeyn.mp3player;

/** Defines how long a stopped or paused session remains visible in the mini-player. */
public final class MiniPlayerRetentionPolicy {
    private MiniPlayerRetentionPolicy() {
    }

    public static boolean isPlaybackActive(boolean playWhenReady, PlaybackPhase phase,
            StopReason stopReason) {
        return playWhenReady
                && phase != PlaybackPhase.ENDED
                && stopReason != StopReason.QUEUE_ENDED;
    }

    static boolean isExpired(boolean playing, long inactiveSince, long legacySavedAt,
            long now, long retentionMs) {
        if (playing) {
            return false;
        }
        if (retentionMs <= 0L) {
            return true;
        }
        long startedAt = inactiveSince > 0L ? inactiveSince : legacySavedAt;
        return startedAt <= 0L || now - startedAt > retentionMs;
    }
}
