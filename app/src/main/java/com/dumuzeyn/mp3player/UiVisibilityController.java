package com.dumuzeyn.mp3player;

import android.view.View;
import android.view.ViewGroup;

/** Pauses visual work only; playback remains in the foreground Media3 service. */
final class UiVisibilityController {
    private UiVisibilityController() {
    }

    static void apply(MainActivityCore host, boolean visible) {
        if (!visible) {
            host.tabsController.cancelScrollAnimation();
            host.swipeController.cancelForBackground();
            if (host.miniPlayer != null) host.miniPlayer.animate().cancel();
        }
        if (host.particleEffectsView != null) {
            host.particleEffectsView.setUiActive(visible);
        }
        if (host.songsView != null) {
            host.songsView.setHostVisible(visible);
        }
        host.playlistController.setUiActive(visible);
        applyToTree(host.root, visible);
        host.playerUiController.onHostVisibilityChanged(visible);
    }

    private static void applyToTree(View view, boolean visible) {
        if (view == null) return;
        if (view instanceof BackgroundMediaView) {
            ((BackgroundMediaView) view).setUiActive(visible);
        } else if (view instanceof PlayerGradientBackground) {
            ((PlayerGradientBackground) view).setUiActive(visible);
        } else if (view instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) view).setUiActive(visible);
        } else if (view instanceof SmoothPlaylistTicker) {
            ((SmoothPlaylistTicker) view).setUiActive(visible);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                applyToTree(group.getChildAt(index), visible);
            }
        }
    }
}
