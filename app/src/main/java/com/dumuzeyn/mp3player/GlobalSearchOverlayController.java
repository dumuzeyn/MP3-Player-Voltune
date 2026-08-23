package com.dumuzeyn.mp3player;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

final class GlobalSearchOverlayController {
    private final MainActivityCore host;
    private final OverlayController overlays;

    GlobalSearchOverlayController(MainActivityCore host, OverlayController overlays) {
        this.host = host;
        this.overlays = overlays;
    }

    void open() {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(12));
        panel.addView(header(shade));
        EditText input = new EditText(host);
        input.setHint(host.tr("Songs, artists, albums, genres, playlists",
                "Песни, исполнители, альбомы, жанры, плейлисты"));
        input.setSingleLine(true);
        input.setText(host.navigationState.search);
        input.setTextColor(host.fg);
        input.setHintTextColor(host.muted);
        panel.addView(input, new LinearLayout.LayoutParams(-1, host.dp(54)));
        ScrollView scroll = new ScrollView(host);
        LinearLayout results = new LinearLayout(host);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        input.addTextChangedListener(watcher(results, shade));
        shade.addView(panel, host.bottomParams());
        host.overlayHost.addView(shade);
        input.requestFocus();
        submit(results, shade, input.getText().toString());
    }

    private LinearLayout header(FrameLayout shade) {
        LinearLayout row = host.uiFactory.row();
        row.addView(host.uiFactory.text(host.tr("Search", "Поиск"), 22, true),
                new LinearLayout.LayoutParams(0, host.dp(52), 1.0f));
        Button close = host.uiFactory.icon("×");
        close.setContentDescription(host.tr("Close", "Закрыть"));
        close.setOnClickListener(view -> close(shade));
        row.addView(close, host.uiFactory.square(48));
        return row;
    }

    private TextWatcher watcher(LinearLayout results, FrameLayout shade) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                submit(results, shade, s == null ? "" : s.toString());
            }
        };
    }

    private void submit(LinearLayout results, FrameLayout shade, String query) {
        host.globalSearchController.search(host.libraryState.tracks,
                host.libraryState.playlists, query, value -> render(results, shade, value));
    }

    private void render(LinearLayout target, FrameLayout shade, GlobalSearchResult result) {
        target.removeAllViews();
        addTracks(target, shade, host.tr("Songs", "Песни"), result.songs);
        addGroups(target, shade, host.tr("Artists", "Исполнители"), result.artists,
                track -> track.artist);
        addGroups(target, shade, host.tr("Albums", "Альбомы"), result.albums,
                track -> track.album);
        addGroups(target, shade, host.tr("Genres", "Жанры"), result.genres,
                track -> track.genre);
        if (!result.playlists.isEmpty()) {
            addHeading(target, host.tr("Playlists", "Плейлисты"));
            for (Playlist playlist : result.playlists) {
                addButton(target, playlist.name, () -> {
                    close(shade);
                    overlays.openPlaylist(playlist);
                });
            }
        }
    }

    private void addTracks(LinearLayout target, FrameLayout shade, String title,
            List<Track> tracks) {
        if (tracks.isEmpty()) {
            return;
        }
        addHeading(target, title);
        for (Track track : tracks) {
            target.addView(host.songsRenderer.songRow(track, true, false,
                    () -> close(shade)));
        }
    }

    private void addGroups(LinearLayout target, FrameLayout shade, String title,
            List<String> values, GroupValue valueProvider) {
        if (values.isEmpty()) {
            return;
        }
        addHeading(target, title);
        for (String value : values) {
            addButton(target, value, () -> {
                close(shade);
                overlays.openGroup(value, groupTracks(value, valueProvider));
            });
        }
    }

    private ArrayList<Track> groupTracks(String value, GroupValue valueProvider) {
        ArrayList<Track> tracks = new ArrayList<>();
        String expected = Track.normalizeSearchText(value);
        for (Track track : host.libraryState.tracks) {
            if (expected.equals(Track.normalizeSearchText(valueProvider.value(track)))) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private void addHeading(LinearLayout target, String value) {
        target.addView(host.uiFactory.text(value, 15, true),
                new LinearLayout.LayoutParams(-1, host.dp(38)));
    }

    private void addButton(LinearLayout target, String label, Runnable action) {
        Button button = host.uiFactory.button(label);
        button.setOnClickListener(view -> action.run());
        target.addView(button, new LinearLayout.LayoutParams(-1, host.dp(50)));
    }

    private void close(FrameLayout shade) {
        host.globalSearchController.cancel();
        if (shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        host.playerUiController.updateMini();
    }

    private interface GroupValue {
        String value(Track track);
    }
}
