package com.dumuzeyn.mp3player;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** Creates the shared current-track marker used by every library surface. */
final class NowPlayingIndicator {
    private NowPlayingIndicator() {
    }

    static View create(MainActivityCore host) {
        View indicator = new View(host);
        style(indicator, host.yellow);
        return indicator;
    }

    static void style(View indicator, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(indicator.getResources().getDimension(
                R.dimen.now_playing_indicator_width));
        indicator.setBackground(background);
    }

    static FrameLayout.LayoutParams layoutParams(Context context) {
        int width = size(context, R.dimen.now_playing_indicator_width);
        int height = size(context, R.dimen.now_playing_indicator_height);
        int inset = size(context, R.dimen.now_playing_indicator_inset);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        params.setMargins(inset, 0, 0, 0);
        return params;
    }

    private static int size(Context context, int resource) {
        return context.getResources().getDimensionPixelSize(resource);
    }
}
