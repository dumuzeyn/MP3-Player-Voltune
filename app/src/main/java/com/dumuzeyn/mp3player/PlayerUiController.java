package com.dumuzeyn.mp3player;

import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

final class PlayerUiController {
    private final MainActivityCore host;
    private final MiniPlayerController miniPlayerController;
    private final FullPlayerController fullPlayerController;

    PlayerUiController(MainActivityCore host, PlaybackActions playbackActions,
            PlaybackStateProvider playbackState) {
        this.host = host;
        this.miniPlayerController =
                new MiniPlayerController(host, playbackActions, playbackState);
        this.fullPlayerController =
                new FullPlayerController(host, playbackActions, playbackState);
    }

    void buildMini(FrameLayout root) {
        miniPlayerController.build(root);
    }

    void openFullPlayer() {
        fullPlayerController.open();
    }

    void updateMini() {
        miniPlayerController.updateState();
    }

    void syncPlaybackUi() {
        miniPlayerController.updateState();
        if (fullPlayerController.isOpen()) {
            fullPlayerController.refresh();
        }
    }

    void onHostVisibilityChanged(boolean visible) {
        fullPlayerController.onHostVisibilityChanged(visible);
    }

    boolean isInsideMiniPlayer(MotionEvent event) {
        return miniPlayerController.isInsideMiniPlayer(event);
    }

    boolean closeFullPlayerIfTop(View top) {
        return fullPlayerController.closeIfTop(top);
    }

    void onHostDestroyed() {
        fullPlayerController.onHostDestroyed();
    }
}
