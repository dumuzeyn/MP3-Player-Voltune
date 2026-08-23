package com.dumuzeyn.mp3player;

/** Pure layout rules for the three-line playlist preview. */
final class PlaylistTickerLayoutPolicy {
    private PlaylistTickerLayoutPolicy() {
    }

    static int visibleLineCount(int titleCount, int maximumLines) {
        if (maximumLines <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(maximumLines, Math.max(0, titleCount)));
    }

    static int staticLinesToDraw(int titleCount, int maximumLines) {
        return Math.max(0, Math.min(maximumLines, titleCount));
    }
}
