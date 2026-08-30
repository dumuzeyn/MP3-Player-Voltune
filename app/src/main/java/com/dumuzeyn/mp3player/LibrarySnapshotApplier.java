package com.dumuzeyn.mp3player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Applies an asynchronously loaded library snapshot to the activity-owned view model. */
final class LibrarySnapshotApplier {
    private final MainActivityCore host;
    private int derivedGeneration;
    private long loadedContentVersion;

    LibrarySnapshotApplier(MainActivityCore host) {
        this.host = host;
    }

    void apply(LibraryLoader.Snapshot snapshot) {
        host.libraryState.tracks.clear();
        host.libraryState.tracks.addAll(snapshot.tracks);
        host.libraryState.favorites.clear();
        host.libraryState.favorites.addAll(snapshot.favorites);
        host.libraryState.playlists.clear();
        host.libraryState.playlists.addAll(snapshot.playlists);
        host.libraryState.homeContent = snapshot.homeContent;
        loadedContentVersion = snapshot.contentVersion;
        host.libraryRepository.reindex();
        host.playbackController.restorePersistedUiState();
        host.playbackController.connect();
        host.render();
        host.soundAnalysisController.onLibraryReady(host.libraryState.tracks);
        host.audioImportController.onLibraryReady();
        if (host.getIntent().getIntExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 0) == 0) {
            host.libraryMaintenanceController.run(
                    host.libraryState.tracks, this::applyMaintenance);
        }
    }

    void refreshHome() {
        long currentVersion = LibraryContentVersion.read(host);
        if (host.libraryState.tracks.isEmpty() || currentVersion == loadedContentVersion) {
            return;
        }
        final long requestedVersion = currentVersion;
        host.libraryLoader.refreshHome(host.libraryState.favorites,
                host.libraryState.playlists, content -> {
                    if (requestedVersion < loadedContentVersion) {
                        return;
                    }
                    loadedContentVersion = requestedVersion;
                    host.libraryState.homeContent = content;
                    if (host.navigationState.tabIndex == LibraryTabs.HOME) {
                        host.render();
                    }
                });
    }

    void rebuildDerivedAndRender() {
        final int generation = ++derivedGeneration;
        host.soundAnalysisController.onLibraryReady(host.libraryState.tracks);
        boolean songsVisible = host.navigationState.tabIndex == LibraryTabs.SONGS;
        if (host.songsView != null) {
            host.songsView.refreshFilteredSource(host.libraryState.tracks);
        }
        if (songsVisible) {
            host.render();
        }
        host.libraryLoader.deriveHome(host.libraryState.tracks, host.libraryState.favorites,
                host.libraryState.playlists, content -> {
                    if (generation != derivedGeneration) {
                        return;
                    }
                    host.libraryState.homeContent = content;
                    if (!songsVisible) {
                        host.render();
                    }
                });
    }

    private void applyMaintenance(List<Track> refreshed, List<Track> unavailable) {
        if (refreshed.isEmpty() && unavailable.isEmpty()) {
            return;
        }
        Map<String, Track> updates = new HashMap<>();
        for (Track track : refreshed) {
            updates.put(track.trackId, track);
        }
        HashSet<String> removedIds = new HashSet<>();
        HashSet<String> removedUris = new HashSet<>();
        for (Track track : unavailable) {
            removedIds.add(track.trackId);
            removedUris.add(track.uri);
            Track stored = host.findTrack(track.trackId);
            if (stored != null) {
                host.playbackQueueController.remove(stored);
            }
        }
        for (int index = host.libraryState.tracks.size() - 1; index >= 0; index--) {
            Track current = host.libraryState.tracks.get(index);
            if (removedIds.contains(current.trackId)) {
                host.libraryState.tracks.remove(index);
                continue;
            }
            Track updated = updates.get(current.trackId);
            if (updated != null) {
                host.libraryState.tracks.set(index, updated);
                host.songRows.refreshMetadata(updated.uri, updated,
                        host.formatTrackDuration(updated));
            }
        }
        for (int index = 0; index < host.playbackUiState.queue.size(); index++) {
            Track updated = updates.get(host.playbackUiState.queue.get(index).trackId);
            if (updated != null) {
                host.playbackUiState.queue.set(index, updated);
            }
        }
        host.libraryState.favorites.removeAll(removedUris);
        for (Playlist playlist : host.libraryState.playlists) {
            playlist.uris.removeAll(removedUris);
        }
        host.libraryRepository.reindex();
        if (!removedUris.isEmpty()) {
            host.saveLibraryState();
        }
        rebuildDerivedAndRender();
    }

    void applyRemovedRecords(List<Track> unavailable) {
        applyMaintenance(java.util.Collections.emptyList(), unavailable);
    }
}
