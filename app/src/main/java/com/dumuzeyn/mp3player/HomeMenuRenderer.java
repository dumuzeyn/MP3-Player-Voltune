package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HomeMenuRenderer implements MenuRenderer {
    private final MainActivityCore host;

    HomeMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    @Override
    public void render() {
        if (host.libraryState.tracks.isEmpty()) {
            TextView empty = host.uiFactory.text(host.tr(
                    "Your library is empty. Add songs or a folder to begin.",
                    "Библиотека пуста. Добавьте песни или папку."), 17, false);
            empty.setPadding(0, host.dp(24), 0, host.dp(24));
            host.list.addView(empty);
            return;
        }
        Track current = host.playbackStateProvider.currentTrack();
        Set<String> shownTracks = new HashSet<>();
        if (current != null) {
            addTracks(host.tr("Continue listening", "Продолжить прослушивание"),
                    java.util.Collections.singletonList(current), shownTracks);
        }
        HomeContent content = host.libraryState.homeContent;
        addTracks(host.tr("Recently played", "Недавно слушали"),
                content.recentlyPlayed, shownTracks);
        addTracks(host.tr("Recently added", "Недавно добавленные"),
                content.recentlyAdded, shownTracks);
        addTracks(host.tr("Most played", "Часто слушаемые"),
                content.mostPlayed, shownTracks);
        addTracks(host.tr("Favorites", "Избранное"), content.favorites, shownTracks);
        addPlaylists(content.playlists);
        addGroups(host.tr("Artists", "Исполнители"), content.artists, true);
        addGroups(host.tr("Albums", "Альбомы"), content.albums, false);
    }

    @Override
    public boolean needsMiniSpacer() {
        return true;
    }

    private void addTracks(String title, List<Track> tracks, Set<String> shownTracks) {
        ArrayList<Track> unique = HomeTrackVisibility.takeUnseen(tracks, shownTracks);
        if (unique.isEmpty()) {
            return;
        }
        addHeading(title);
        for (Track track : unique) {
            host.list.addView(host.songsRenderer.songRow(track, true, false));
        }
    }

    private void addPlaylists(List<Playlist> playlists) {
        if (playlists.isEmpty()) {
            return;
        }
        addHeading(host.tr("Recent playlists", "Последние плейлисты"));
        for (Playlist playlist : playlists) {
            addButton(playlist.name, host.appearanceState.playlistCardOpacity,
                    () -> host.overlayController.openPlaylist(playlist));
        }
    }

    private void addGroups(String title, List<String> values, boolean artist) {
        if (values.isEmpty()) {
            return;
        }
        addHeading(title);
        LinearLayout row = host.uiFactory.row();
        for (String value : values.subList(0, Math.min(3, values.size()))) {
            Button button = host.uiFactory.button(value);
            button.setSingleLine(true);
            button.setEllipsize(TextUtils.TruncateAt.END);
            host.uiFactory.applySecondaryButtonStyle(button,
                    artist ? host.appearanceState.artistCardOpacity
                            : host.appearanceState.albumCardOpacity);
            button.setOnClickListener(view -> host.overlayController.openGroup(
                    value, matchingTracks(value, artist)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, host.dp(52), 1.0f);
            params.setMargins(host.dp(2), 0, host.dp(2), 0);
            row.addView(button, params);
        }
        host.list.addView(row);
    }

    private ArrayList<Track> matchingTracks(String value, boolean artist) {
        java.util.Map<String, ArrayList<Track>> groups = artist
                ? host.libraryState.homeContent.artistTracks
                : host.libraryState.homeContent.albumTracks;
        ArrayList<Track> result = groups.get(value);
        return result == null ? new ArrayList<>() : new ArrayList<>(result);
    }

    private void addHeading(String value) {
        TextView heading = host.uiFactory.text(value, 18, true);
        heading.setPadding(0, host.dp(14), 0, host.dp(4));
        host.list.addView(heading, new LinearLayout.LayoutParams(-1, host.dp(50)));
    }

    private void addButton(String label, int opacity, Runnable action) {
        Button button = host.uiFactory.button(label);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        host.uiFactory.applySecondaryButtonStyle(button, opacity);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(52));
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        host.list.addView(button, params);
    }
}
