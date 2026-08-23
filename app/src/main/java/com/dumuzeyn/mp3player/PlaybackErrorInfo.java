package com.dumuzeyn.mp3player;

import androidx.annotation.Nullable;

public final class PlaybackErrorInfo {
    public final int code;
    public final String category;
    public final boolean recoverable;
    public final String safeMessage;
    public final String mediaId;

    public PlaybackErrorInfo(int code, String category, boolean recoverable,
            @Nullable String safeMessage, @Nullable String mediaId) {
        this.code = code;
        this.category = safe(category, "unknown");
        this.recoverable = recoverable;
        this.safeMessage = safe(safeMessage, "Playback error");
        this.mediaId = safe(mediaId, "");
    }

    private static String safe(@Nullable String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String sanitized = value.replaceAll("(?i)(content|file)://\\S+", "[media]");
        return sanitized.length() <= 160 ? sanitized : sanitized.substring(0, 160);
    }
}
