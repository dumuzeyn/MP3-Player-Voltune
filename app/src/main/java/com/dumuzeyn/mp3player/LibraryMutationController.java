package com.dumuzeyn.mp3player;

import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Coordinates committed SQLite removals with the activity and Media3 projections. */
final class LibraryMutationController implements AutoCloseable {
    private final MainActivityCore host;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    LibraryMutationController(MainActivityCore host) {
        this.host = host;
    }

    void removeTrack(Track track) {
        if (track == null) {
            return;
        }
        execute(() -> {
            LibraryMutationStore store = new LibraryMutationStore(host);
            try {
                publish(store.removeTrack(track));
            } finally {
                store.close();
            }
        });
    }

    void removeSource(LibrarySource source) {
        if (source == null) {
            return;
        }
        execute(() -> {
            publish(PersistedFolderStore.forget(host, source));
        });
    }

    void clearLibrary() {
        execute(() -> {
            publish(PersistedFolderStore.clear(host));
        });
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }

    private void execute(Runnable mutation) {
        try {
            executor.execute(() -> {
                try {
                    mutation.run();
                } catch (RuntimeException error) {
                    VoltuneLog.failure("library_mutation_failed", error);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    private void publish(RemovedLibraryItems removed) {
        LibraryMutationClock.advance();
        for (LibrarySource source : removed.sources) {
            PersistedFolderStore.releaseReadPermission(host, source.asUri());
        }
        host.uiHandler.post(() -> applyToUi(removed));
    }

    private void applyToUi(RemovedLibraryItems removed) {
        if (closed) {
            return;
        }
        if (removed.clearQueue) {
            host.playbackQueueController.clear();
            host.playbackUiState.queue.clear();
        } else {
            host.playbackQueueController.removeCommitted(removed.trackIds, removed.trackUris);
        }
        host.libraryState.tracks.removeIf(track -> removed.trackIds.contains(track.trackId));
        host.libraryState.favorites.removeAll(removed.trackUris);
        for (Playlist playlist : host.libraryState.playlists) {
            playlist.uris.removeAll(removed.trackUris);
        }
        host.libraryRepository.reindex();
        host.librarySnapshotApplier.rebuildDerivedAndRender();
    }
}
