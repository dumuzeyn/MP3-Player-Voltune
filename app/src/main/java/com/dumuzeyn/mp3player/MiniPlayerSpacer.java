package com.dumuzeyn.mp3player;

import android.view.View;
import android.widget.LinearLayout;

/** Reserves scroll space only while the mini player is visible. */
final class MiniPlayerSpacer {
    private MiniPlayerSpacer() {
    }

    static void addIfNeeded(MainActivityCore host) {
        int currentIndex = host.currentTrackIndex();
        if (currentIndex < 0 || currentIndex >= host.libraryState.tracks.size()
                || host.overlayHost.getChildCount() > 0) {
            return;
        }
        View spacer = new View(host);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, host.dp(88)));
        host.list.addView(spacer);
    }
}
