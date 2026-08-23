package com.dumuzeyn.mp3player.ui.layout;

import android.app.Activity;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.FrameLayout;

/** Supplies phone and tablet dimensions while keeping screen behavior identical. */
public final class ResponsiveLayoutController {
    public static final int TABLET_MIN_WIDTH_DP = 600;

    private final Activity activity;

    public ResponsiveLayoutController(Activity activity) {
        this.activity = activity;
    }

    public boolean isTablet() {
        Configuration configuration = activity.getResources().getConfiguration();
        int smallestWidthDp = configuration.smallestScreenWidthDp;
        if (smallestWidthDp <= 0) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            smallestWidthDp = Math.round(
                    Math.min(metrics.widthPixels, metrics.heightPixels) / metrics.density);
        }
        return isTabletWidth(smallestWidthDp);
    }

    public static boolean isTabletWidth(int smallestWidthDp) {
        return smallestWidthDp >= TABLET_MIN_WIDTH_DP;
    }

    public int pageHorizontalPadding() {
        return dp(isTablet() ? 20 : 8);
    }

    public int pageTopPadding() {
        return dp(isTablet() ? 18 : 14);
    }

    public FrameLayout.LayoutParams mainPageParams() {
        if (!isTablet()) {
            return new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                boundedWidth(960, 32),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        return params;
    }

    public FrameLayout.LayoutParams fullPlayerContentParams() {
        if (!isTablet()) {
            return new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
        }
        return new FrameLayout.LayoutParams(
                boundedWidth(640, 40),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    }

    public FrameLayout.LayoutParams miniPlayerParams() {
        if (!isTablet()) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, dp(74), Gravity.BOTTOM);
            params.setMargins(dp(10), 0, dp(10), dp(10));
            return params;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                boundedWidth(720, 40), dp(78), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(0, 0, 0, dp(18));
        return params;
    }

    public FrameLayout.LayoutParams centeredPanelParams(int requestedWidth, int requestedHeight) {
        int width = boundedPanelWidth(requestedWidth, screenWidth(), dp(28));
        int height = requestedHeight;
        if (isTablet()) {
            width = boundedPanelWidth(
                    Math.max(requestedWidth, dp(440)), screenWidth(), dp(48));
            if (requestedHeight > 0) {
                height = Math.min(requestedHeight, Math.max(dp(240), screenHeight() - dp(64)));
            }
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width, height, Gravity.CENTER);
        params.setMargins(dp(14), dp(14), dp(14), dp(14));
        return params;
    }

    static int boundedPanelWidth(int requestedWidth, int screenWidth, int totalMargin) {
        return Math.min(requestedWidth, Math.max(1, screenWidth - totalMargin));
    }

    public FrameLayout.LayoutParams bottomPanelParams() {
        if (!isTablet()) {
            return new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Math.round(screenHeight() * 0.78f),
                    Gravity.BOTTOM);
        }
        int height = Math.min(dp(760), Math.round(screenHeight() * 0.88f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                boundedWidth(720, 40), height, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(0, 0, 0, dp(20));
        return params;
    }

    public int fullPlayerCoverSizeDp(int screenHeightDp) {
        if (!isTablet()) {
            return screenHeightDp < 760 ? 225 : 260;
        }
        if (screenHeightDp < 700) {
            return 230;
        }
        return screenHeightDp < 900 ? 280 : 320;
    }

    private int boundedWidth(int maximumDp, int totalMarginDp) {
        return Math.min(dp(maximumDp), Math.max(dp(280), screenWidth() - dp(totalMarginDp)));
    }

    private int screenWidth() {
        return activity.getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return activity.getResources().getDisplayMetrics().heightPixels;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
