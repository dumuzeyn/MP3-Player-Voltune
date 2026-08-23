package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(8));
        host.uiFactory.setSurface(card, host.panel, false, host.appearanceState.playlistCardOpacity);

        View marker = new View(host);
        marker.setBackgroundColor(host.yellow);

        LinearLayout header = host.uiFactory.row();
        LinearLayout titleColumn = new LinearLayout(host);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        TextView title = host.uiFactory.text(playlist.name, 20, true);
        host.uiFactory.makeMarquee(title);
        TextView count = host.uiFactory.text(playlist.uris.size() + " " + host.tr3("songs", "песен", "♪"), 13, false);
        titleColumn.addView(title);
        titleColumn.addView(count);
        header.addView(titleColumn, new LinearLayout.LayoutParams(0, -2, 1.0f));

        Button delete = host.uiFactory.icon("×");
        host.uiFactory.applyPlainIconStyle(delete, Color.rgb(190, 45, 45));
        delete.setOnClickListener(view -> host.overlayController.confirmDeletePlaylist(playlist));
        header.addView(delete, host.uiFactory.square(44));

        Button rename = host.uiFactory.icon("✎");
        host.uiFactory.applyPlainIconStyle(rename);
        rename.setOnClickListener(view -> host.overlayController.renamePlaylist(playlist));
        header.addView(rename, host.uiFactory.square(44));

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
        header.addView(play, host.uiFactory.square(44));

        Button shuffle = host.uiFactory.shuffleButton();
        host.uiFactory.applyPlainIconStyle(shuffle);
        shuffle.setOnClickListener(view -> {
            host.playbackQueueController.playList(tracks, true);
        });
        header.addView(shuffle, host.uiFactory.square(44));
        card.addView(header);

        LinearLayout body = host.uiFactory.row();
        FrameLayoutCover cover = new FrameLayoutCover(host);
        int fallback = host.appearanceState.dark ? 28 : 235;
        cover.setFallback(Color.rgb(fallback, fallback, fallback));
        body.addView(cover, host.uiFactory.square(72));

        SmoothPlaylistTicker ticker = new SmoothPlaylistTicker(host);
        ticker.setPadding(host.dp(12), 0, 0, 0);
        body.addView(ticker, new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView(body);
        host.playlistController.bindRollingPreview(ticker, cover, tracks, host.navigationState.songRenderGeneration);

        card.setOnClickListener(view -> host.overlayController.openPlaylist(playlist));

        FrameLayout container = new FrameLayout(host);
        container.addView(card, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams markerParams = new FrameLayout.LayoutParams(host.dp(4), -1);
        markerParams.gravity = android.view.Gravity.START;
        markerParams.setMargins(host.dp(2), host.dp(10), 0, host.dp(10));
        container.addView(marker, markerParams);
        host.playlistController.bindPlaybackState(
                play, marker, tracks, host.navigationState.songRenderGeneration);
        return container;
    }
}
