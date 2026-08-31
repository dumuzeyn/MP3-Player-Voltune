package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

final class PlaylistsMenuRenderer implements MenuRenderer {
    private final MainActivityCore host;

    PlaylistsMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    @Override
    public boolean needsMiniSpacer() {
        return true;
    }

    @Override
    public void render() {
        host.playlistController.beginPlaybackBindings(host.navigationState.songRenderGeneration);
        ArrayList<Playlist> playlists = host.playlistController.filteredPlaylists(host.navigationState.search);
        if (playlists.isEmpty()) {
            TextView empty = host.uiFactory.text(host.tr3("No playlists yet", "Плейлистов пока нет", "∅ ▤"), 18, true);
            empty.setPadding(host.dp(12), host.dp(24), host.dp(12), host.dp(24));
            host.list.addView(empty);
            return;
        }
        int limit = playlists.size();
        for (int index = 0; index < limit; index++) {
            Playlist playlist = playlists.get(index);
            host.list.addView(host.uiFactory.spaced(playlistCard(playlist)));
        }
    }

    private View playlistCard(final Playlist playlist) {
        final ArrayList<Track> tracks = host.playlistController.sortedPlaylistTracks(playlist);
        LinearLayout card = new LinearLayout(host);
        card.setId(R.id.playlist_card);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(host.dp(7), host.dp(4), host.dp(6), host.dp(4));
        host.uiFactory.setSurface(card, host.panel, false, host.appearanceState.playlistCardOpacity);

        FrameLayoutCover cover = new FrameLayoutCover(host);
        int fallback = host.appearanceState.dark ? 28 : 235;
        cover.setFallback(Color.rgb(fallback, fallback, fallback));
        int coverSize = host.getResources().getDimensionPixelSize(R.dimen.playlist_cover_size);
        card.addView(cover, new LinearLayout.LayoutParams(coverSize, coverSize));

        LinearLayout titleColumn = new LinearLayout(host);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setPadding(host.dp(10), 0, host.dp(5), 0);
        TextView title = host.uiFactory.text(playlist.name, 17, true);
        host.uiFactory.makeMarquee(title);
        TextView count = host.uiFactory.text(playlist.uris.size() + " "
                + host.tr3("tracks", "треков", "♪"), 12, false);
        titleColumn.addView(title, new LinearLayout.LayoutParams(-1, host.dp(22)));
        titleColumn.addView(count, new LinearLayout.LayoutParams(-1, host.dp(16)));

        SmoothPlaylistTicker ticker = new SmoothPlaylistTicker(host);
        ticker.setVisibleLines(1);
        ticker.setTextSizeSp(12);
        titleColumn.addView(ticker, new LinearLayout.LayoutParams(-1, host.dp(20)));
        card.addView(titleColumn, new LinearLayout.LayoutParams(0, -1, 1.0f));

        LinearLayout actions = host.uiFactory.row();
        int actionSize = host.getResources().getDimensionPixelSize(R.dimen.playlist_action_size);

        Button delete = host.uiFactory.icon("×");
        host.uiFactory.applyPlainIconStyle(delete, Color.rgb(190, 45, 45));
        delete.setOnClickListener(view -> host.overlayController.confirmDeletePlaylist(playlist));
        actions.addView(delete, new LinearLayout.LayoutParams(actionSize, actionSize));

        Button rename = host.uiFactory.icon("✎");
        host.uiFactory.applyPlainIconStyle(rename);
        rename.setOnClickListener(view -> host.overlayController.renamePlaylist(playlist));
        actions.addView(rename, new LinearLayout.LayoutParams(actionSize, actionSize));

        Button play = host.uiFactory.icon(host.playbackQueueController.isPlayingCollection(tracks) ? "Ⅱ" : "▶");
        host.uiFactory.applyPlainIconStyle(play, host.purple);
        SongRowStateRegistry.applyPlayState(play, host.playbackQueueController.isPlayingCollection(tracks));
        play.setOnClickListener(view -> {
            if (host.playbackQueueController.isCurrentCollection(tracks)) {
                host.playbackQueueController.toggleOrStart();
            } else {
                host.playbackQueueController.playList(tracks, false);
            }
        });
        actions.addView(play, new LinearLayout.LayoutParams(actionSize, actionSize));

        Button shuffle = host.uiFactory.shuffleButton();
        host.uiFactory.applyPlainIconStyle(shuffle);
        shuffle.setOnClickListener(view -> {
            host.playbackQueueController.playList(tracks, true);
        });
        actions.addView(shuffle, new LinearLayout.LayoutParams(actionSize, actionSize));
        card.addView(actions, new LinearLayout.LayoutParams(actionSize * 4, actionSize));
        host.playlistController.bindRollingPreview(ticker, cover, tracks, host.navigationState.songRenderGeneration);

        card.setOnClickListener(view -> host.overlayController.openPlaylist(playlist));

        FrameLayout container = new FrameLayout(host);
        int cardHeight = host.getResources().getDimensionPixelSize(R.dimen.playlist_card_height);
        container.addView(card, new FrameLayout.LayoutParams(-1, cardHeight));
        View marker = NowPlayingIndicator.create(host);
        container.addView(marker, NowPlayingIndicator.layoutParams(host));
        host.playlistController.bindPlaybackState(
                play, marker, tracks, host.navigationState.songRenderGeneration);
        container.setMinimumHeight(cardHeight);
        return container;
    }
}
