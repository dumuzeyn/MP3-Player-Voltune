package com.dumuzeyn.mp3player;

import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Immutable data required to commit a tab transition. */
final class TabTransitionRequest {
    final int targetTab;
    final int direction;
    final boolean recordHistory;
    final String searchQuery;
    final ScrollView scrollView;
    final LinearLayout content;
    final MainRenderer.PreviewState previewState;

    TabTransitionRequest(int targetTab, int direction, boolean recordHistory,
            String searchQuery, ScrollView scrollView, LinearLayout content,
            MainRenderer.PreviewState previewState) {
        this.targetTab = targetTab;
        this.direction = direction;
        this.recordHistory = recordHistory;
        this.searchQuery = searchQuery == null ? "" : searchQuery;
        this.scrollView = scrollView;
        this.content = content;
        this.previewState = previewState;
    }

    static TabTransitionRequest withoutPreview(int targetTab, int direction,
            boolean recordHistory, String searchQuery) {
        return new TabTransitionRequest(targetTab, direction, recordHistory,
                searchQuery, null, null, null);
    }
}
