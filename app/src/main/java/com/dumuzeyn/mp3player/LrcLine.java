package com.dumuzeyn.mp3player;

final class LrcLine {
    final long timeMs;
    final String text;

    LrcLine(long timeMs, String text) {
        this.timeMs = Math.max(0L, timeMs);
        this.text = text == null ? "" : text;
    }
}
