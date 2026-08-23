package com.dumuzeyn.mp3player;

import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Owns the full-screen surface; page-specific UI lives in dedicated controllers. */
final class FullPlayerController {
    private final MainActivityCore host;
    private final PlaybackActions actions;
    private final PlaybackStateProvider playbackState;
    private FrameLayout currentSheet;
    private FullPlayerPagerController pager;
    private boolean hostVisible = true;

    FullPlayerController(MainActivityCore host, PlaybackActions actions,
            PlaybackStateProvider playbackState) {
        this.host = host;
        this.actions = actions;
        this.playbackState = playbackState;
    }

    void open() {
        if (playbackState.currentTrack() == null) {
            return;
        }
        if (isOpen()) {
            refresh();
            return;
        }
        if (host.miniPlayer != null) {
            host.miniPlayer.setVisibility(View.GONE);
        }
        FrameLayout sheet = new FullPlayerSheet(host, value -> close(value, true));
        currentSheet = sheet;
        addBackground(sheet);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.dp(10), host.dp(10), host.dp(10), host.dp(10));
        sheet.addView(content, host.responsiveLayoutController.fullPlayerContentParams());
        addHeader(content, sheet);
        pager = new FullPlayerPagerController(host, actions, playbackState);
        content.addView(pager.createPager(), new LinearLayout.LayoutParams(-1, 0, 1.0f));
        content.addView(pager.createIndicator(), new LinearLayout.LayoutParams(-1, host.dp(18)));
        pager.setHostVisible(hostVisible);

        boolean animate = host.appearanceState.animations
                && host.navigationState.fullPlayerOpening;
        host.navigationState.fullPlayerOpening = false;
        host.overlayHost.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        if (animate) {
            sheet.setTranslationY(host.getResources().getDisplayMetrics().heightPixels);
            sheet.animate().translationY(0.0f).setDuration(145L)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    void refresh() {
        if (pager != null) {
            pager.refresh();
        }
    }

    void onHostVisibilityChanged(boolean visible) {
        hostVisible = visible;
        if (pager != null) {
            pager.setHostVisible(visible);
        }
    }

    boolean isOpen() {
        return currentSheet != null && currentSheet.getParent() != null;
    }

    boolean closeIfTop(View top) {
        if (top != currentSheet || !isOpen()) {
            return false;
        }
        close(currentSheet, true);
        return true;
    }

    void onHostDestroyed() {
        releasePager();
        currentSheet = null;
    }

    private void addHeader(LinearLayout content, FrameLayout sheet) {
        LinearLayout row = host.uiFactory.row();
        Button back = host.uiFactory.icon("←");
        back.setTextSize(34.0f);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setContentDescription(host.tr("Close player", "Закрыть плеер"));
        back.setOnClickListener(view -> close(sheet, false));
        row.addView(back, host.uiFactory.square(54));
        row.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1.0f));
        content.addView(row, new LinearLayout.LayoutParams(-1, host.dp(56)));
    }

    private void addBackground(FrameLayout sheet) {
        sheet.setBackgroundColor(host.appearanceState.playerSolidBackground == 0
                ? host.bg : host.appearanceState.playerSolidBackground);
        if (host.appearanceState.playerBackgroundMode
                == BackgroundSettingsController.MODE_GRADIENT) {
            sheet.addView(new PlayerGradientBackground(host,
                    new PlayerGradientBackground.Config() {
                        @Override public boolean animationsEnabled() {
                            return hostVisible && host.appearanceState.animations;
                        }
                        @Override public boolean darkTheme() {
                            return host.appearanceState.dark;
                        }
                        @Override public int baseColor() {
                            return host.bg;
                        }
                    }, host.appearanceState.playerGradientStart,
                    host.appearanceState.playerGradientEnd), fill());
        } else if (host.appearanceState.playerBackgroundMode
                == BackgroundSettingsController.MODE_MEDIA
                && !host.appearanceState.playerBackgroundMediaUri.isEmpty()) {
            sheet.addView(new BackgroundMediaView(host,
                    host.appearanceState.playerBackgroundMediaUri,
                    host.appearanceState.playerBackgroundBlur, host.bg), fill());
        }
    }

    private FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private void close(FrameLayout sheet, boolean animate) {
        if (sheet == null || sheet.getParent() == null) {
            host.playerUiController.updateMini();
            return;
        }
        if (pager != null) pager.setHostVisible(false);
        if (animate && host.appearanceState.animations) {
            sheet.animate().translationY(host.getResources().getDisplayMetrics().heightPixels)
                    .alpha(0.0f).setDuration(135L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        releasePager();
                        removeSheet(sheet);
                    }).start();
        } else {
            releasePager();
            removeSheet(sheet);
        }
        if (sheet == currentSheet) {
            currentSheet = null;
        }
    }

    private void removeSheet(FrameLayout sheet) {
        if (sheet.getParent() != null) {
            host.overlayHost.removeView(sheet);
        }
        host.playerUiController.updateMini();
    }

    private void releasePager() {
        if (pager != null) {
            pager.close();
            pager = null;
        }
    }
}
