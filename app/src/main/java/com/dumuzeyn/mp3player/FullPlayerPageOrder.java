package com.dumuzeyn.mp3player;

final class FullPlayerPageOrder {
    static final int PLAYER = 0;
    static final int LYRICS = 1;
    static final int QUEUE = 2;

    private FullPlayerPageOrder() {
    }

    static int afterRightSwipe(int position) {
        return Math.max(PLAYER, position - 1);
    }

    static int afterLeftSwipe(int position) {
        return Math.min(QUEUE, position + 1);
    }
}
