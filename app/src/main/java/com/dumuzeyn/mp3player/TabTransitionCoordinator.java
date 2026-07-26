package com.dumuzeyn.mp3player;

import android.view.View;
import android.widget.ScrollView;

/** Commits tab navigation without keeping transition mechanics in the Activity. */
final class TabTransitionCoordinator {
    interface ContentBinding {
        void bind(ScrollView scrollView, android.widget.LinearLayout content);
    }

    interface Navigation {
        int selectedTab();

        void setSelectedTab(int value);

        String searchQuery();

        void setSearchQuery(String value);

        void setPreferredDirection(int value);

        void setTransitionRunning(boolean value);
    }

    private final Navigation navigation;
    private final MainScreenView screen;
    private final MainRenderer renderer;
    private final BackNavigationController backNavigation;
    private final TabsController tabs;
    private final SongsRenderer songs;
    private final ContentBinding contentBinding;

    TabTransitionCoordinator(Navigation navigation, MainScreenView screen,
            MainRenderer renderer, BackNavigationController backNavigation,
            TabsController tabs, SongsRenderer songs, ContentBinding contentBinding) {
        this.navigation = navigation;
        this.screen = screen;
        this.renderer = renderer;
        this.backNavigation = backNavigation;
        this.tabs = tabs;
        this.songs = songs;
        this.contentBinding = contentBinding;
    }

    void complete(TabTransitionRequest request) {
        recordHistoryIfNeeded(request);
        applyNavigation(request);
        renderer.render();
        tabs.finishTransition(request.targetTab);
    }

    void completeWithPreview(TabTransitionRequest request) {
        recordHistoryIfNeeded(request);
        renderer.captureCurrentScrollPosition();
        ScrollView previous = screen.contentScroll();
        if (previous != null && previous.getParent() == screen.contentHost()) {
            screen.contentHost().removeView(previous);
        }
        request.scrollView.setTranslationX(0.0f);
        request.scrollView.setAlpha(1.0f);
        request.scrollView.setVisibility(View.VISIBLE);
        request.scrollView.setOnScrollChangeListener(
                (view, scrollX, scrollY, oldScrollX, oldScrollY) ->
                        songs.loadMoreIfNearBottom());
        screen.replaceContent(request.scrollView, request.content);
        contentBinding.bind(request.scrollView, request.content);
        applyNavigation(request);
        renderer.adoptPreview(
                request.targetTab, request.searchQuery, request.previewState);
        tabs.refreshTabs();
        tabs.finishTransition(request.targetTab);
    }

    private void recordHistoryIfNeeded(TabTransitionRequest request) {
        if (request.recordHistory) {
            backNavigation.recordTabState(
                    navigation.selectedTab(), navigation.searchQuery());
        }
    }

    private void applyNavigation(TabTransitionRequest request) {
        navigation.setPreferredDirection(request.direction);
        navigation.setSelectedTab(request.targetTab);
        navigation.setSearchQuery(request.searchQuery);
        navigation.setTransitionRunning(false);
    }
}
