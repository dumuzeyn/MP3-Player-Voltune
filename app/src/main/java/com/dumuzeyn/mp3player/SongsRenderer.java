package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

final class SongsRenderer {
    private final MainActivityCore host;
    private ArrayList<Track> pendingTracks;
    private int pendingStart;
    private int pendingGeneration = -1;
    private int renderedStart;
    private int rowStartChildIndex;
    private View topSpacer;
    private int nextRenderScrollY = -1;

    SongsRenderer(MainActivityCore host) {
        this.host = host;
    }

    void close() {
    }

    void render(ArrayList<Track> tracks) {
        renderSongsState(tracks);
    }

    void renderLibrary(ArrayList<Track> tracks, String query) {
        if (!host.navigationState.renderingTabPreview && host.songsView != null) {
            host.songsView.show(tracks, query);
            return;
        }
        renderSongsState(host.libraryListController.filter(tracks));
    }

    void renderSongsState(ArrayList<Track> tracks) {
        pendingTracks = null;
        pendingStart = 0;
        pendingGeneration = -1;
        renderedStart = 0;
        topSpacer = null;
        String title;
        String titleRu;
        if (tracks.isEmpty()) {
            if (host.navigationState.tabIndex == LibraryTabs.SONGS) {
                title = "Add MP3 or another audio file";
                titleRu = "Добавьте MP3 или другой аудиофайл";
            } else {
                title = "Nothing here yet";
                titleRu = "Здесь пока пусто";
            }
            TextView empty = host.uiFactory.text(host.tr(title, titleRu), 18, true);
            empty.setPadding(host.dp(12), host.dp(24), host.dp(12), host.dp(24));
            host.list.addView(empty);
            host.addMiniSpacerIfNeeded();
            return;
        }
        pendingTracks = new ArrayList<>(tracks);
        pendingGeneration = host.navigationState.songRenderGeneration;
        rowStartChildIndex = host.list.getChildCount();
        int initialScrollY = nextRenderScrollY;
        nextRenderScrollY = -1;
        if (initialScrollY > 0 && initializeWindow(initialScrollY)) {
            return;
        }
        appendNextSongBatch();
    }

    void prepareNextRenderForScroll(int scrollY) {
        nextRenderScrollY = Math.max(0, scrollY);
    }

    void loadMoreIfNearBottom() {
        if (host.contentScroll == null || pendingTracks == null) {
            return;
        }
        prependPreviousBatchIfNeeded();
        if (pendingStart >= pendingTracks.size()) {
            return;
        }
        View child = host.contentScroll.getChildAt(0);
        if (child == null || child.getBottom() - (host.contentScroll.getHeight()
                + host.contentScroll.getScrollY()) > host.dp(900)) {
            return;
        }
        appendNextSongBatch();
    }

    void prepareForScrollRestore(int scrollY) {
        if (scrollY <= 0 || pendingTracks == null) {
            return;
        }
        initializeWindow(scrollY);
    }

    private boolean initializeWindow(int scrollY) {
        int rowHeight = estimatedRowHeight();
        int targetStart = Math.max(0, scrollY / rowHeight - 8);
        if (targetStart <= 0 || targetStart < pendingStart) {
            return false;
        }
        while (host.list.getChildCount() > rowStartChildIndex) {
            host.list.removeViewAt(host.list.getChildCount() - 1);
        }
        host.activeSongRows().clear();
        renderedStart = targetStart;
        pendingStart = targetStart;
        topSpacer = new View(host);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(
                -1, targetStart * rowHeight));
        host.list.addView(topSpacer);
        appendNextSongBatch();
        return true;
    }

    private void prependPreviousBatchIfNeeded() {
        if (renderedStart <= 0 || topSpacer == null || pendingTracks == null) {
            return;
        }
        int spacerHeight = renderedStart * estimatedRowHeight();
        if (host.contentScroll.getScrollY() > spacerHeight + host.dp(700)) {
            return;
        }
        int newStart = Math.max(0, renderedStart - 15);
        int insertionIndex = rowStartChildIndex + 1;
        for (int index = newStart; index < renderedStart; index++) {
            host.list.addView(songRow(pendingTracks.get(index), true, true),
                    insertionIndex++);
        }
        renderedStart = newStart;
        if (renderedStart == 0) {
            host.list.removeView(topSpacer);
            topSpacer = null;
        } else {
            topSpacer.getLayoutParams().height = renderedStart * estimatedRowHeight();
            topSpacer.requestLayout();
        }
    }

    private int estimatedRowHeight() {
        return Math.max(1, host.dp(66));
    }

    BatchState captureBatchState() {
        return new BatchState(pendingTracks, pendingStart, pendingGeneration,
                renderedStart, rowStartChildIndex, topSpacer);
    }

    void restoreBatchState(BatchState state) {
        pendingTracks = state.pendingTracks;
        pendingStart = state.pendingStart;
        pendingGeneration = state.pendingGeneration;
        renderedStart = state.renderedStart;
        rowStartChildIndex = state.rowStartChildIndex;
        topSpacer = state.topSpacer;
    }

    static final class BatchState {
        final ArrayList<Track> pendingTracks;
        final int pendingStart;
        final int pendingGeneration;
        final int renderedStart;
        final int rowStartChildIndex;
        final View topSpacer;

        BatchState(ArrayList<Track> pendingTracks, int pendingStart, int pendingGeneration,
                int renderedStart, int rowStartChildIndex, View topSpacer) {
            this.pendingTracks = pendingTracks;
            this.pendingStart = pendingStart;
            this.pendingGeneration = pendingGeneration;
            this.renderedStart = renderedStart;
            this.rowStartChildIndex = rowStartChildIndex;
            this.topSpacer = topSpacer;
        }
    }

    private void appendNextSongBatch() {
        if (pendingTracks == null || pendingGeneration != host.navigationState.songRenderGeneration
                || (host.navigationState.tabIndex != LibraryTabs.SONGS
                && host.navigationState.tabIndex != LibraryTabs.FAVORITES)) {
            pendingTracks = null;
            return;
        }
        if (pendingStart >= pendingTracks.size()) {
            return;
        }
        ArrayList<Track> tracksToRender = pendingTracks;
        int start = pendingStart;
        int batchSize = 15;
        int end = Math.min(tracksToRender.size(), start + batchSize);
        for (int i = start; i < end; i++) {
            host.list.addView(songRow(tracksToRender.get(i), true, true));
        }
        pendingStart = end;
        if (end >= tracksToRender.size()) {
            host.addMiniSpacerIfNeeded();
        }
    }

    View songRow(Track track, boolean showActions, boolean showFavoriteAction) {
        return songRow(track, showActions, showFavoriteAction, null);
    }

    View songRow(final Track track, boolean showActions, boolean showFavoriteAction, final Runnable afterPlay) {
        return songRow(track, showActions, showFavoriteAction, afterPlay, null);
    }

    View songRow(final Track track, boolean showActions, boolean showFavoriteAction, final Runnable afterPlay,
            final Runnable actionOverride) {
        FrameLayout container = new FrameLayout(host);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4));
        host.uiFactory.applyCardStyle(row, host.navigationState.tabIndex == LibraryTabs.FAVORITES
                ? host.appearanceState.favoriteCardOpacity : host.appearanceState.songCardOpacity);

        View marker = NowPlayingIndicator.create(host);
        marker.setVisibility(host.isCurrent(track) ? View.VISIBLE : View.INVISIBLE);
        host.activeSongRows().registerCurrentMarker(track.uri, marker);

        ImageView cover = host.uiFactory.coverView();
        host.artworkUi.loadCover(cover, track, host.purpleSoft);
        View.OnClickListener openOrPlay = view -> TrackTapController.handle(
                host, track, cover);
        cover.setOnClickListener(openOrPlay);
        row.setOnClickListener(openOrPlay);
        row.addView(cover, host.uiFactory.square(52));

        LinearLayout textColumn = new LinearLayout(host);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(host.dp(10), 0, host.dp(6), 0);
        TextView title = host.uiFactory.text(track.title, 16, true);
        title.setTextColor(host.primaryText);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title);

        LinearLayout metaRow = new LinearLayout(host);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(16);
        WaveformView waveform = host.artworkUi.createWaveform(
                track, host.isCurrent(track));
        host.activeSongRows().registerWaveform(track.uri, waveform);
        metaRow.addView(waveform, new LinearLayout.LayoutParams(0, host.dp(26), 1.0f));
        TextView duration = host.uiFactory.text(host.formatTrackDuration(track), 12, false);
        duration.setGravity(17);
        duration.setTextColor(host.secondaryText);
        metaRow.addView(duration, new LinearLayout.LayoutParams(host.dp(46), host.dp(26)));
        host.activeSongRows().registerMetadata(track.uri, title, duration);
        textColumn.addView(metaRow);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, host.dp(62), 1.0f));

        if (host.navigationState.tabIndex == LibraryTabs.FAVORITES) {
            Button favorite = host.uiFactory.icon(host.libraryState.favorites.contains(track.uri) ? "♥︎" : "♡︎");
            favorite.setTextSize(14.0f);
            host.uiFactory.applyPlainIconStyle(favorite, host.libraryState.favorites.contains(track.uri) ? host.purple : host.secondaryText);
            favorite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    host.toggleFavorite(track);
                    host.render();
                }
            });
            row.addView(favorite, host.uiFactory.square(40));
        } else if (showActions) {
            Button actions = host.uiFactory.icon("⋯");
            host.uiFactory.applyPlainIconStyle(actions);
            actions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (actionOverride != null) {
                        actionOverride.run();
                    } else {
                        host.overlayController.openSongActions(track);
                    }
                }
            });
            row.addView(actions, host.uiFactory.square(44));
        }

        Button play = host.uiFactory.icon("");
        host.uiFactory.applyPrimaryButtonStyle(play);
        SongRowStateRegistry.applyPlayState(play,
                host.isCurrent(track) && host.isPlaybackPlaying());
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (host.isCurrent(track)) {
                    host.playbackQueueController.toggleOrStart();
                } else {
                    host.playbackQueueController.playTrack(track, true);
                }
                if (afterPlay != null) {
                    afterPlay.run();
                }
            }
        });
        host.activeSongRows().registerPlayButton(track.uri, play);
        row.addView(play, host.uiFactory.square(44));
        container.addView(row, new FrameLayout.LayoutParams(-1, -2));
        container.addView(marker, NowPlayingIndicator.layoutParams(host));
        return host.uiFactory.spaced(container);
    }

    View queueRow(final Track track, final Runnable removeAction, final Runnable playAction) {
        FrameLayout container = new FrameLayout(host);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4));
        host.uiFactory.setSurface(row, host.panel, false,
                host.appearanceState.songCardOpacity);

        View marker = NowPlayingIndicator.create(host);
        marker.setVisibility(host.isCurrent(track) ? View.VISIBLE : View.INVISIBLE);

        ImageView cover = host.uiFactory.coverView();
        host.artworkUi.loadCover(cover, track,
                host.appearanceState.dark ? android.graphics.Color.rgb(28, 28, 28)
                        : android.graphics.Color.rgb(235, 235, 235));
        row.addView(cover, host.uiFactory.square(52));

        TextView title = host.uiFactory.text(track.title, 17, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(host.dp(12), 0, host.dp(8), 0);
        title.setTextColor(host.fg);
        row.addView(title, new LinearLayout.LayoutParams(0, host.dp(62), 1.0f));

        Button remove = host.uiFactory.icon("−");
        host.uiFactory.applyPlainIconStyle(remove, android.graphics.Color.rgb(190, 45, 45));
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeAction.run();
            }
        });
        row.addView(remove, host.uiFactory.square(44));

        Button play = host.uiFactory.icon("");
        host.uiFactory.applyPlainIconStyle(play, host.purple);
        SongRowStateRegistry.applyPlayState(play,
                host.isCurrent(track) && host.isPlaybackPlaying());
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playAction.run();
            }
        });
        row.addView(play, host.uiFactory.square(44));
        container.addView(row, new FrameLayout.LayoutParams(-1, -2));
        container.addView(marker, NowPlayingIndicator.layoutParams(host));
        return host.uiFactory.spaced(container);
    }
}
