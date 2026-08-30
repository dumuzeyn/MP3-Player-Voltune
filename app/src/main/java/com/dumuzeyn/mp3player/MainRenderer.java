package com.dumuzeyn.mp3player;

import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import java.util.HashMap;
import java.util.Map;

final class MainRenderer {
    private final MainActivityCore host;
    private final MenuRenderer homeRenderer;
    private final SongsMenuRenderer songsRenderer;
    private final FavoritesMenuRenderer favoritesRenderer;
    private final PlaylistsMenuRenderer playlistsRenderer;
    private final MenuRenderer soundRenderer;
    private final MenuRenderer genresRenderer;
    private final MenuRenderer artistsRenderer;
    private final MenuRenderer albumsRenderer;
    private final MenuRenderer settingsRenderer;
    private final MenuRenderer foldersRenderer;
    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private String renderedMenuKey;

    MainRenderer(MainActivityCore host) {
        this.host = host;
        this.homeRenderer = new HomeMenuRenderer(host);
        this.songsRenderer = new SongsMenuRenderer(host);
        this.favoritesRenderer = new FavoritesMenuRenderer(host);
        this.playlistsRenderer = new PlaylistsMenuRenderer(host);
        this.soundRenderer = new SoundMenuRenderer(host);
        this.genresRenderer = new GenresMenuRenderer(host);
        this.artistsRenderer = new ArtistsMenuRenderer(host);
        this.albumsRenderer = new AlbumsMenuRenderer(host);
        this.settingsRenderer = new SettingsMenuRenderer(host);
        this.foldersRenderer = new FoldersMenuRenderer(host);
    }

    void render() {
        rememberCurrentScrollPosition();
        host.refreshTabs();
        host.navigationState.songRenderGeneration++;
        if (host.navigationState.tabIndex == LibraryTabs.SONGS
                && !host.navigationState.renderingTabPreview
                && host.songsView != null) {
            host.songRows.clear();
            host.sourcePlayButton = null;
            if (host.contentScroll != null) {
                host.contentScroll.setVisibility(View.GONE);
            }
            songsRenderer.render();
            renderedMenuKey = menuKey(
                    host.navigationState.tabIndex, host.navigationState.search);
            host.playerUiController.updateMini();
            return;
        }
        if (host.songsView != null) {
            host.songsView.hide();
        }
        if (host.contentScroll != null) {
            host.contentScroll.setVisibility(View.VISIBLE);
        }
        host.list.removeAllViews();
        host.songRows.clear();
        host.sourcePlayButton = null;
        host.renderSectionHeader();
        MenuRenderer renderer = rendererForTab();
        int scrollY = scrollPositionFor(
                host.navigationState.tabIndex, host.navigationState.search);
        if (host.navigationState.tabIndex == LibraryTabs.SONGS
                || host.navigationState.tabIndex == LibraryTabs.FAVORITES) {
            host.songsRenderer.prepareNextRenderForScroll(scrollY);
        }
        renderer.render();
        if (renderer.needsMiniSpacer()) {
            host.addMiniSpacerIfNeeded();
        }
        restoreCurrentScrollPosition();
        host.playerUiController.updateMini();
    }

    void captureScrollBeforeUiRebuild() {
        rememberCurrentScrollPosition();
        renderedMenuKey = null;
    }

    void captureCurrentScrollPosition() {
        rememberCurrentScrollPosition();
    }

    private void rememberCurrentScrollPosition() {
        if (renderedMenuKey == null || host.contentScroll == null) {
            return;
        }
        if (renderedMenuKey.startsWith(LibraryTabs.SONGS + "\n")) {
            return;
        }
        scrollPositions.put(renderedMenuKey, Math.max(0, host.contentScroll.getScrollY()));
    }

    private void restoreCurrentScrollPosition() {
        renderedMenuKey = menuKey(host.navigationState.tabIndex, host.navigationState.search);
        final String targetKey = renderedMenuKey;
        if (host.contentScroll == null) {
            return;
        }
        int scrollY = scrollPositions.containsKey(renderedMenuKey)
                ? scrollPositions.get(renderedMenuKey) : 0;
        final ScrollView targetScroll = host.contentScroll;
        if (scrollY <= 0) {
            targetScroll.scrollTo(0, 0);
            targetScroll.setVisibility(View.VISIBLE);
            return;
        }
        targetScroll.setVisibility(View.INVISIBLE);
        targetScroll.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        ViewTreeObserver observer = targetScroll.getViewTreeObserver();
                        if (observer.isAlive()) {
                            observer.removeOnPreDrawListener(this);
                        }
                        if (host.contentScroll != targetScroll
                                || !targetKey.equals(menuKey(host.navigationState.tabIndex, host.navigationState.search))) {
                            targetScroll.setVisibility(View.VISIBLE);
                            return true;
                        }
                        targetScroll.scrollTo(0, scrollY);
                        targetScroll.setVisibility(View.VISIBLE);
                        return false;
                    }
                });
    }

    private String menuKey(int tabIndex, String search) {
        return tabIndex + "\n" + (search == null ? "" : search);
    }

    PreviewState renderPreview(android.widget.LinearLayout target, int targetIndex, String targetSearch) {
        android.widget.LinearLayout previousList = host.list;
        ButtonState previousButton = new ButtonState(host.sourcePlayButton);
        SongsRenderer.BatchState previousBatchState = host.songsRenderer.captureBatchState();
        int previousTab = host.navigationState.tabIndex;
        int previousGeneration = host.navigationState.songRenderGeneration;
        String previousSearch = host.navigationState.search;
        boolean previousPreview = host.navigationState.renderingTabPreview;
        int scrollY = scrollPositionFor(targetIndex, targetSearch);
        PreviewState previewState;
        try {
            host.previewSongRows.clear();
            host.list = target;
            host.navigationState.tabIndex = targetIndex;
            host.navigationState.songRenderGeneration = previousGeneration + 1;
            host.navigationState.search = targetSearch == null ? "" : targetSearch;
            host.navigationState.renderingTabPreview = true;
            host.sourcePlayButton = null;
            target.removeAllViews();
            host.renderSectionHeader();
            MenuRenderer renderer = rendererForTab(targetIndex);
            if (targetIndex == LibraryTabs.SONGS
                    || targetIndex == LibraryTabs.FAVORITES) {
                host.songsRenderer.prepareNextRenderForScroll(scrollY);
            }
            renderer.render();
            if (renderer.needsMiniSpacer()) {
                host.addMiniSpacerIfNeeded();
            }
            previewState = new PreviewState(
                    scrollY,
                    host.navigationState.songRenderGeneration,
                    host.songsRenderer.captureBatchState(),
                    host.sourcePlayButton);
        } finally {
            host.list = previousList;
            host.navigationState.tabIndex = previousTab;
            host.navigationState.songRenderGeneration = previousGeneration;
            host.navigationState.search = previousSearch;
            host.navigationState.renderingTabPreview = previousPreview;
            host.sourcePlayButton = previousButton.button;
            host.songsRenderer.restoreBatchState(previousBatchState);
        }
        return previewState;
    }

    void adoptPreview(int targetIndex, String targetSearch, PreviewState state) {
        if (host.songsView != null) {
            host.songsView.hide();
        }
        renderedMenuKey = menuKey(targetIndex, targetSearch);
        host.navigationState.songRenderGeneration = state.generation;
        host.songsRenderer.restoreBatchState(state.batchState);
        host.songRows.replaceWith(host.previewSongRows);
        host.previewSongRows.clear();
        host.sourcePlayButton = state.sourcePlayButton;
        host.songRows.refresh(host.songRowStateResolver());
        host.artworkUi.promoteVisibleArtwork();
        host.playerUiController.updateMini();
    }

    void discardPreview() {
        host.previewSongRows.clear();
    }

    private int scrollPositionFor(int tabIndex, String search) {
        Integer position = scrollPositions.get(menuKey(tabIndex, search));
        return position == null ? 0 : Math.max(0, position);
    }

    private MenuRenderer rendererForTab() {
        return rendererForTab(host.navigationState.tabIndex);
    }

    private MenuRenderer rendererForTab(int tabIndex) {
        if (tabIndex == LibraryTabs.HOME) {
            return homeRenderer;
        }
        if (tabIndex == LibraryTabs.SONGS) {
            return songsRenderer;
        }
        if (tabIndex == LibraryTabs.FAVORITES) {
            return favoritesRenderer;
        }
        if (tabIndex == LibraryTabs.PLAYLISTS) {
            return playlistsRenderer;
        }
        if (tabIndex == LibraryTabs.SOUND) {
            return soundRenderer;
        }
        if (tabIndex == LibraryTabs.GENRES) {
            return genresRenderer;
        }
        if (tabIndex == LibraryTabs.ARTISTS) {
            return artistsRenderer;
        }
        if (tabIndex == LibraryTabs.ALBUMS) {
            return albumsRenderer;
        }
        if (tabIndex == LibraryTabs.FOLDERS) {
            return foldersRenderer;
        }
        if (tabIndex == LibraryTabs.SETTINGS) {
            return settingsRenderer;
        }
        return songsRenderer;
    }

    private static final class ButtonState {
        final android.widget.Button button;

        ButtonState(android.widget.Button button) {
            this.button = button;
        }
    }

    static final class PreviewState {
        final int scrollY;
        final int generation;
        final SongsRenderer.BatchState batchState;
        final android.widget.Button sourcePlayButton;

        PreviewState(int scrollY, int generation, SongsRenderer.BatchState batchState,
                android.widget.Button sourcePlayButton) {
            this.scrollY = scrollY;
            this.generation = generation;
            this.batchState = batchState;
            this.sourcePlayButton = sourcePlayButton;
        }
    }
}
