package com.dumuzeyn.mp3player;

/** Applies an asynchronously loaded library snapshot to the activity-owned view model. */
final class LibrarySnapshotApplier {
    private final MainActivityCore host;

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
        host.libraryRepository.reindex();
        host.playbackController.restorePersistedUiState();
        host.playbackController.connect();
        host.render();
        host.songsRenderer.refreshMissingMetadataAsync();
        host.audioImportController.onLibraryReady();
    }

    void refreshHome() {
        if (host.libraryState.tracks.isEmpty()) {
            return;
        }
        host.libraryLoader.refreshHome(host.libraryState.favorites,
                host.libraryState.playlists, content -> {
                    host.libraryState.homeContent = content;
                    if (host.navigationState.tabIndex == LibraryTabs.HOME) {
                        host.render();
                    }
                });
    }
}
