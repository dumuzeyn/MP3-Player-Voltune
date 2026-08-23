package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

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
        if (current != null) {
            addTracks(host.tr("Continue listening", "Продолжить прослушивание"),
                    java.util.Collections.singletonList(current));
        }
        HomeContent content = host.libraryState.homeContent;
        addTracks(host.tr("Recently played", "Недавно слушали"), content.recentlyPlayed);
        addTracks(host.tr("Recently added", "Недавно добавленные"), content.recentlyAdded);
        addTracks(host.tr("Most played", "Часто слушаемые"), content.mostPlayed);
        addTracks(host.tr("Favorites", "Избранное"), content.favorites);
        addPlaylists(content.playlists);
        addGroups(host.tr("Artists", "Исполнители"), content.artists, true);
        addGroups(host.tr("Albums", "Альбомы"), content.albums, false);
    }

    @Override
    public boolean needsMiniSpacer() {
        return true;
    }

    private void addTracks(String title, List<Track> tracks) {
        if (tracks.isEmpty()) {
            return;
        }
        addHeading(title);
        for (Track track : tracks) {
            host.list.addView(host.songsRenderer.songRow(track, true, false));
        }
    }

    private void addPlaylists(List<Playlist> playlists) {
        if (playlists.isEmpty()) {
            return;
        }
        addHeading(host.tr("Recent playlists", "Последние плейлисты"));
        for (Playlist playlist : playlists) {
            addButton(playlist.name, () -> host.overlayController.openPlaylist(playlist));
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
            button.setOnClickListener(view -> host.overlayController.openGroup(
                    value, matchingTracks(value, artist)));
            row.addView(button, new LinearLayout.LayoutParams(0, host.dp(52), 1.0f));
        }
        host.list.addView(row);
    }

    private ArrayList<Track> matchingTracks(String value, boolean artist) {
        ArrayList<Track> result = new ArrayList<>();
        String normalized = Track.normalizeSearchText(value);
        for (Track track : host.libraryState.tracks) {
            String candidate = artist ? track.artist : track.album;
            if (normalized.equals(Track.normalizeSearchText(candidate))) {
                result.add(track);
            }
        }
        return result;
    }

    private void addHeading(String value) {
        TextView heading = host.uiFactory.text(value, 18, true);
        heading.setPadding(0, host.dp(14), 0, host.dp(4));
        host.list.addView(heading, new LinearLayout.LayoutParams(-1, host.dp(50)));
    }

    private void addButton(String label, Runnable action) {
        Button button = host.uiFactory.button(label);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setOnClickListener(view -> action.run());
        host.list.addView(button, new LinearLayout.LayoutParams(-1, host.dp(52)));
    }
}
