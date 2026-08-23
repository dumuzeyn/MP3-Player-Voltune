package com.dumuzeyn.mp3player;

final class FullPlayerPageOrder {
    static final int QUEUE = 0;
    static final int LYRICS = 1;
    static final int PLAYER = 2;

    private FullPlayerPageOrder() {
    }

    static int afterRightSwipe(int position) {
        return Math.max(QUEUE, position - 1);
    }

    static int afterLeftSwipe(int position) {
        return Math.min(PLAYER, position + 1);
    }
}
