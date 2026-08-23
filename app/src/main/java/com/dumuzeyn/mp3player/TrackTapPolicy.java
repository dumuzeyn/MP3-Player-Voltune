package com.dumuzeyn.mp3player;

final class TrackTapPolicy {
    enum Action { PLAY, OPEN_PLAYER }

    private TrackTapPolicy() {
    }

    static Action action(boolean current) {
        return current ? Action.OPEN_PLAYER : Action.PLAY;
    }
}
