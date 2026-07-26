package com.dumuzeyn.mp3player;

import android.view.View;
import java.util.ArrayDeque;

final class BackNavigationController {
    private static final int MAX_HISTORY = 32;

    private final MainActivityCore host;
    private final ArrayDeque<TabState> tabHistory = new ArrayDeque<>();

    BackNavigationController(MainActivityCore host) {
        this.host = host;
    }

    void recordTabState(int tabIndex, String search) {
        TabState latest = tabHistory.peekLast();
        if (latest != null && latest.tabIndex == tabIndex && latest.search.equals(safe(search))) {
            return;
        }
        if (tabHistory.size() >= MAX_HISTORY) {
            tabHistory.removeFirst();
        }
        tabHistory.addLast(new TabState(tabIndex, safe(search)));
    }

    boolean handleBack() {
        if (host.navigationState.tabAnimating) {
            return true;
        }
        if (host.overlayHost != null && host.overlayHost.getChildCount() > 0) {
            View top = host.overlayHost.getChildAt(host.overlayHost.getChildCount() - 1);
            if (!host.playerUiController.closeFullPlayerIfTop(top)) {
                host.overlayHost.removeView(top);
                host.playerUiController.updateMini();
            }
            return true;
        }
        TabState previous = tabHistory.pollLast();
        if (previous == null) {
            return false;
        }
        host.restoreTabFromBack(previous.tabIndex, previous.search);
        return true;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class TabState {
        final int tabIndex;
        final String search;

        TabState(int tabIndex, String search) {
            this.tabIndex = tabIndex;
            this.search = search;
        }
    }
}
