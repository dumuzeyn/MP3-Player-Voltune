package com.dumuzeyn.mp3player

import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Coordinates committed SQLite removals with the activity and Media3 projections. */
internal class LibraryMutationController(private val host: MainActivityCore) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false

    fun removeTrack(track: Track?) {
        removeTrack(track, false)
    }

    fun removeDeletedFile(track: Track?) {
        removeTrack(track, true)
    }

    fun removeSource(source: LibrarySource?) {
        if (source == null) return
        execute { publish(PersistedFolderStore.forget(host, source)) }
    }

    fun clearLibrary() {
        execute { publish(PersistedFolderStore.clear(host)) }
    }

    override fun close() {
        closed = true
        executor.shutdownNow()
    }

    private fun removeTrack(track: Track?, fileWasDeleted: Boolean) {
        if (track == null) return
        execute {
            LibraryMutationStore(host).use { store ->
                publish(
                    if (fileWasDeleted) store.removeDeletedFile(track) else store.removeTrack(track),
                )
            }
        }
    }

    private fun execute(mutation: () -> Unit) {
        try {
            executor.execute {
                try {
                    mutation()
                } catch (error: RuntimeException) {
                    VoltuneLog.failure("library_mutation_failed", error)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Activity is already closing.
        }
    }

    private fun publish(removed: RemovedLibraryItems) {
        removed.sources.forEach { source ->
            PersistedFolderStore.releaseReadPermission(host, source.asUri())
        }
        host.uiHandler.post { applyToUi(removed) }
    }

    private fun applyToUi(removed: RemovedLibraryItems) {
        if (closed) return
        if (removed.clearQueue) {
            host.playbackQueueController.clear()
            host.playbackUiState.queue.clear()
        } else {
            host.playbackQueueController.removeCommitted(removed.trackIds, removed.trackUris)
        }
        for (index in host.libraryState.tracks.lastIndex downTo 0) {
            if (host.libraryState.tracks[index].trackId in removed.trackIds) {
                host.libraryState.tracks.removeAt(index)
            }
        }
        host.libraryState.favorites.removeAll(removed.trackUris)
        host.libraryState.playlists.forEach { playlist ->
            playlist.uris.removeAll(removed.trackUris)
        }
        host.libraryRepository.reindex()
        host.librarySnapshotApplier.rebuildDerivedAndRender()
    }
}
