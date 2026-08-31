package com.dumuzeyn.mp3player;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

final class OverlayController implements AutoCloseable {
    interface SelectionDone {
        void done(Set<String> selected);
    }

    private final MainActivityCore host;
    private final QueueOverlayController queueController;
    private final GlobalSearchOverlayController searchController;
    private final TrackSelectionOverlayController selectionController;
    private final TrackDeletionController deletionController;

    OverlayController(MainActivityCore host) {
        this.host = host;
        this.queueController = new QueueOverlayController(host, this);
        this.searchController = new GlobalSearchOverlayController(host, this);
        this.selectionController = new TrackSelectionOverlayController(host);
        this.deletionController = new TrackDeletionController(host);
    }

    void openGroup(String title, ArrayList<Track> tracks) {
        openTrackPanel(title, tracks, null);
    }

    void openPlaylist(final Playlist playlist) {
        openTrackPanel(playlist.name, host.playlistController.playlistTracks(playlist), playlist);
    }

    private void openTrackPanel(String title, ArrayList<Track> tracks, Playlist playlist) {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        LinearLayout header = host.uiFactory.row();
        header.addView(host.uiFactory.text(title, 20, true), new LinearLayout.LayoutParams(0, host.dp(58), 1.0f));
        Button play = host.uiFactory.icon(host.playbackQueueController.isPlayingSource(tracks) ? "Ⅱ" : "▶");
        play.setOnClickListener(view -> {
            if (host.playbackQueueController.isPlayingSource(tracks)) {
                host.playbackQueueController.toggleOrStart();
            } else {
                host.playbackQueueController.playList(tracks, false);
            }
        });
        header.addView(play, host.uiFactory.square(52));
        Button shuffle = host.uiFactory.shuffleButton();
        shuffle.setOnClickListener(view -> host.playbackQueueController.playList(tracks, true));
        header.addView(shuffle, host.uiFactory.square(52));
        if (playlist != null) {
            Button add = host.uiFactory.icon("+");
            add.setOnClickListener(view -> {
                host.overlayHost.removeView(shade);
                openAddToPlaylist(playlist);
            });
            header.addView(add, host.uiFactory.square(52));
        }
        Button close = host.uiFactory.icon("×");
        close.setOnClickListener(view -> close(shade));
        header.addView(close, host.uiFactory.square(52));
        panel.addView(header);

        ScrollView scroll = new ScrollView(host);
        LinearLayout rows = new LinearLayout(host);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (Track track : tracks) {
            rows.addView(trackPanelRow(track, tracks, playlist, shade, title));
        }
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        shade.addView(panel, host.bottomParams());
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private FrameLayout.LayoutParams compactSongActionsParams() {
        int availableWidth = host.getResources().getDisplayMetrics().widthPixels - host.dp(28);
        int width = Math.min(host.dp(420), Math.max(host.dp(280), availableWidth));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(host.dp(14), 0, host.dp(14), host.dp(14));
        return params;
    }

    private View trackPanelRow(Track track, ArrayList<Track> source, Playlist playlist,
                               FrameLayout shade, String title) {
        LinearLayout container = new LinearLayout(host);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(host.songsRenderer.songRow(track, true, true, () -> {
            host.overlayHost.removeView(shade);
            if (playlist == null) {
                openGroup(title, source);
            } else {
                openPlaylist(playlist);
            }
        }, () -> {
            host.overlayHost.removeView(shade);
            openSongActions(track, playlist);
        }));
        return container;
    }

    void openQueue() {
        queueController.open();
    }

    void refreshPlayback() {
        queueController.refreshPlayback();
    }

    void openAddFavorites() {
        openSelection(host.tr("Add to favorites", "Добавить в избранное"),
                new HashSet<>(), selected -> {
                    host.libraryState.favorites.addAll(selected);
                    host.saveLibraryState();
            host.librarySnapshotApplier.rebuildDerivedAndRender();
                });
    }

    private void openAddToPlaylist(Playlist playlist) {
        openSelection(host.tr("Add to ", "Добавить в ") + playlist.name,
                new HashSet<>(), selected -> {
                    host.playlistController.addTracksToPlaylist(playlist, selected);
                    host.overlayHost.removeAllViews();
                    openPlaylist(playlist);
                });
    }

    void openSelection(String title, HashSet<String> selected, SelectionDone done) {
        this.selectionController.open(title, selected, done);
    }

    void openSongActions(Track track) {
        openSongActions(track, null);
    }

    void openSongActions(Track track, Playlist sourcePlaylist) {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
        TextView title = host.uiFactory.text(track.title, 19, true);
        title.setTextColor(host.purple);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(host.dp(12), 0, host.dp(12), 0);
        panel.addView(title, new LinearLayout.LayoutParams(-1, host.dp(42)));
        addCompactPanelButton(panel, host.libraryState.favorites.contains(track.uri)
                ? host.tr("Remove from favorites", "Убрать из избранного")
                : host.tr("Add to favorites", "Добавить в избранное"), () -> {
            host.toggleFavorite(track);
            close(shade);
        });
        addCompactPanelButton(panel, host.tr("Add to playlist", "Добавить в плейлист"), () -> {
            host.overlayHost.removeView(shade);
            choosePlaylist(track);
        });
        addCompactPanelButton(panel, host.tr("Play next", "Играть следующим"), () -> {
            host.playbackQueueController.playNext(track);
            close(shade);
        });
        addCompactPanelButton(panel, host.tr("Add to end of queue", "В конец очереди"), () -> {
            host.playbackQueueController.add(track);
            close(shade);
        });
        addCompactPanelButton(panel, host.tr("Edit metadata", "Изменить метаданные"), () -> {
            close(shade);
            host.metadataEditorController.open(track);
        });
        if (sourcePlaylist != null) {
            addCompactPanelButton(panel, host.tr("Remove from playlist", "Убрать из плейлиста"), () -> {
                sourcePlaylist.uris.remove(track.uri);
                host.saveLibraryState();
                host.overlayHost.removeView(shade);
                openPlaylist(sourcePlaylist);
            });
        }
        addCompactPanelButton(panel, host.tr("Remove from library", "Убрать из медиатеки"), () -> {
            host.overlayHost.removeView(shade);
            confirmRemoveTrack(track);
        });
        if (deletionController.canDeleteFile(track)) {
            addCompactPanelButton(panel, host.tr("Delete file from device",
                    "Удалить файл с устройства"), () -> {
                host.overlayHost.removeView(shade);
                confirmDeleteFile(track);
            });
        }
        addCompactPanelButton(panel, host.tr("Close", "Закрыть"), () -> close(shade));
        shade.addView(panel, compactSongActionsParams());
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private void choosePlaylist(Track track) {
        openCollectionChooser(track, false);
    }

    void chooseCollection(Track track) {
        openCollectionChooser(track, true);
    }

    private void openCollectionChooser(Track track, boolean includeFavorites) {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.addView(host.uiFactory.dialogTitle(includeFavorites
                        ? host.tr("Save track", "Сохранить песню")
                        : host.tr("Add to playlist", "Добавить в плейлист")),
                host.uiFactory.dialogTitleParams());
        ScrollView scroll = new ScrollView(host);
        LinearLayout rows = new LinearLayout(host);
        rows.setOrientation(LinearLayout.VERTICAL);
        if (includeFavorites) {
            addPanelButton(rows, host.libraryState.favorites.contains(track.uri)
                    ? host.tr("Remove from favorites", "Убрать из избранного")
                    : host.tr("Add to favorites", "Добавить в избранное"), () -> {
                host.toggleFavorite(track);
                close(shade);
                if (host.navigationState.tabIndex == LibraryTabs.FAVORITES) {
                    host.render();
                }
                host.playerUiController.syncPlaybackUi();
            });
        }
        for (Playlist playlist : host.libraryState.playlists) {
            boolean alreadyAdded = playlist.uris.contains(track.uri);
            addPanelButton(rows, alreadyAdded
                    ? playlist.name + " " + host.tr("(added)", "(добавлено)")
                    : playlist.name, () -> {
                host.playlistController.addTrackToPlaylist(playlist, track);
                close(shade);
                if (host.navigationState.tabIndex == LibraryTabs.PLAYLISTS) {
                    host.render();
                }
                host.playerUiController.syncPlaybackUi();
            });
        }
        addPanelButton(rows, host.tr("Create new", "Создать новый"), () -> {
            host.overlayHost.removeView(shade);
            showInput(host.tr("New playlist", "Новый плейлист"),
                    host.tr("Playlist name", "Название плейлиста"), "", false,
                    value -> {
                        host.playlistController.createPlaylistWithTrack(value, track);
                        if (host.navigationState.tabIndex == LibraryTabs.PLAYLISTS) {
                            host.render();
                        }
                    });
        });
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        shade.addView(panel, host.centerParams(host.dp(330), host.dp(420)));
        host.overlayHost.addView(shade);
    }

    void createPlaylist() {
        showInput(host.tr("Create playlist", "Создать плейлист"),
                host.tr("Playlist name", "Название плейлиста"), "", false,
                value -> {
                    host.playlistController.createPlaylist(value);
                    if (host.navigationState.tabIndex == LibraryTabs.PLAYLISTS) {
                        host.render();
                    }
                });
    }

    void renamePlaylist(Playlist playlist) {
        showInput(host.tr("Rename playlist", "Переименовать плейлист"),
                host.tr("Playlist name", "Название плейлиста"), playlist.name, false,
                value -> {
                    host.playlistController.renamePlaylist(playlist, value);
                    host.render();
                });
    }

    void confirmDeletePlaylist(Playlist playlist) {
        host.showConfirmPanel(host.tr("Delete playlist?", "Удалить плейлист?"),
                host.tr("Songs will stay in the app.", "Песни останутся в приложении."),
                () -> {
                    host.playlistController.deletePlaylist(playlist);
                    host.render();
                });
    }

    private void confirmRemoveTrack(Track track) {
        host.showConfirmPanel(host.tr("Remove from library?", "Убрать из медиатеки?"),
                host.tr("The song will disappear from the app, but the file will stay on the phone.",
                        "Песня исчезнет из приложения, но файл останется на телефоне."), () -> {
                    host.playbackQueueController.removeFromLibrary(track);
                });
    }

    private void confirmDeleteFile(Track track) {
        host.showConfirmPanel(host.tr("Delete file from device?",
                        "Удалить файл с устройства?"),
                host.tr("This cannot be undone. The song will also be removed from the library.",
                        "Это действие нельзя отменить. Песня также исчезнет из медиатеки."),
                () -> deletionController.deleteFile(track));
    }

    boolean handleActivityResult(int requestCode, int resultCode) {
        return deletionController.handleActivityResult(requestCode, resultCode);
    }

    void openSearch() {
        searchController.open();
    }

    void showInput(String title, String hint, String value, boolean numeric, MainActivityCore.InputDone done) {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(title), host.uiFactory.dialogTitleParams());
        EditText input = searchField(hint);
        input.setText(value);
        input.setSelection(input.length());
        input.setInputType(numeric ? 2 : 1);
        panel.addView(input, searchParams());
        LinearLayout actions = host.uiFactory.row();
        Button cancel = host.uiFactory.button(host.tr("Cancel", "Отмена"));
        cancel.setOnClickListener(view -> close(shade));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        Button save = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(save);
        save.setOnClickListener(view -> {
            String result = input.getText().toString();
            close(shade);
            done.done(result);
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        panel.addView(actions);
        shade.addView(panel, host.centerParams(host.dp(330), -2));
        host.overlayHost.addView(shade);
        input.requestFocus();
    }

    private void removeFromQueue(Track track) {
        host.playbackQueueController.remove(track);
    }

    private void playQueueTrack(Track track) {
        int index = host.playbackQueueController.indexOf(track);
        if (index < 0) {
            return;
        }
        host.playbackQueueController.playIndex(index, 0);
        host.render();
    }

    private boolean isInQueue(Track track) {
        for (Track queued : host.playbackUiState.queue) {
            if (queued.uri.equals(track.uri)) {
                return true;
            }
        }
        return false;
    }

    private EditText searchField(String hint) {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextColor(host.fg);
        input.setHintTextColor(host.muted);
        input.setTextSize(16.0f);
        input.setPadding(host.dp(14), 0, host.dp(14), 0);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        host.uiFactory.setSurface(input, host.panel, true);
        return input;
    }

    private LinearLayout.LayoutParams searchParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(58));
        params.setMargins(0, host.dp(8), 0, host.dp(12));
        return params;
    }

    private void addPanelButton(LinearLayout panel, String label, Runnable action) {
        Button button = host.uiFactory.button(label);
        button.setOnClickListener(view -> action.run());
        panel.addView(button, new LinearLayout.LayoutParams(-1, host.dp(54)));
    }

    private void addCompactPanelButton(LinearLayout panel, String label, Runnable action) {
        Button button = host.uiFactory.button(label);
        button.setTextSize(16.0f);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(host.dp(12), 0, host.dp(12), 0);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(46));
        params.setMargins(0, host.dp(1), 0, host.dp(1));
        panel.addView(button, params);
    }

    private void close(FrameLayout shade) {
        if (shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        host.playerUiController.updateMini();
    }

    @Override
    public void close() {
        deletionController.close();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void afterTextChanged(Editable s) { }
    }
}
