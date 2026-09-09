package com.dumuzeyn.mp3player

/** Applies an asynchronously loaded library snapshot to the activity-owned view model. */
internal class LibrarySnapshotApplier(private val host: MainActivityCore) {
    private var derivedGeneration = 0
    private var loadedContentVersion = 0L
    @Volatile private var initialSnapshotApplied = false

    fun apply(snapshot: LibraryLoader.Snapshot) {
        host.libraryState.tracks.clear()
        host.libraryState.tracks.addAll(snapshot.tracks)
        host.libraryState.favorites.clear()
        host.libraryState.favorites.addAll(snapshot.favorites)
        host.libraryState.playlists.clear()
        host.libraryState.playlists.addAll(snapshot.playlists)
        host.libraryState.homeContent = snapshot.homeContent
        loadedContentVersion = snapshot.contentVersion
        host.libraryRepository.reindex()
        host.playbackController.restorePersistedUiState()
        host.playbackController.connect()
        host.render()
        initialSnapshotApplied = true
        host.soundAnalysisController.onLibraryReady(host.libraryState.tracks)
        host.volumeLevelingController.onLibraryReady(host.libraryState.tracks)
        host.audioImportController.onLibraryReady()
        if (host.intent.getIntExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 0) == 0) {
            host.libraryMaintenanceController.run(host.libraryState.tracks, ::applyMaintenance)
        }
    }

    fun hasAppliedInitialSnapshot(): Boolean = initialSnapshotApplied

    fun refreshHome() {
        val currentVersion = LibraryContentVersion.read(host)
        if (host.libraryState.tracks.isEmpty() || currentVersion == loadedContentVersion) return
        val requestedVersion = currentVersion
        host.libraryLoader.refreshHome(
            host.libraryState.favorites,
            host.libraryState.playlists,
        ) { content ->
            if (requestedVersion < loadedContentVersion) return@refreshHome
            loadedContentVersion = requestedVersion
            host.libraryState.homeContent = content
            if (host.navigationState.tabIndex == LibraryTabs.HOME) host.render()
        }
    }

    fun rebuildDerivedAndRender() {
        val generation = ++derivedGeneration
        host.soundAnalysisController.onLibraryReady(host.libraryState.tracks)
        host.volumeLevelingController.onLibraryReady(host.libraryState.tracks)
        val songsVisible = host.navigationState.tabIndex == LibraryTabs.SONGS
        host.songsView?.refreshFilteredSource(host.libraryState.tracks)
        if (songsVisible) host.render()
        host.libraryLoader.deriveHome(
            host.libraryState.tracks,
            host.libraryState.favorites,
            host.libraryState.playlists,
        ) { content ->
            if (generation != derivedGeneration) return@deriveHome
            host.libraryState.homeContent = content
            if (!songsVisible) host.render()
        }
    }

    private fun applyMaintenance(refreshed: List<Track>, unavailable: List<Track>) {
        if (refreshed.isEmpty() && unavailable.isEmpty()) return
        val updates = HashMap<String, Track>()
        refreshed.forEach { track -> updates[track.trackId] = track }
        val removedIds = HashSet<String>()
        val removedUris = HashSet<String>()
        unavailable.forEach { track ->
            removedIds.add(track.trackId)
            removedUris.add(track.uri)
            host.findTrack(track.trackId)?.let(host.playbackQueueController::remove)
        }
        for (index in host.libraryState.tracks.lastIndex downTo 0) {
            val current = host.libraryState.tracks[index]
            if (current.trackId in removedIds) {
                host.libraryState.tracks.removeAt(index)
                continue
            }
            updates[current.trackId]?.let { updated ->
                host.libraryState.tracks[index] = updated
                host.songRows.refreshMetadata(
                    updated.uri,
                    updated,
                    host.formatTrackDuration(updated),
                )
            }
        }
        for (index in host.playbackUiState.queue.indices) {
            updates[host.playbackUiState.queue[index].trackId]?.let { updated ->
                host.playbackUiState.queue[index] = updated
            }
        }
        host.libraryState.favorites.removeAll(removedUris)
        host.libraryState.playlists.forEach { playlist -> playlist.uris.removeAll(removedUris) }
        host.libraryRepository.reindex()
        if (removedUris.isNotEmpty()) host.saveLibraryState()
        rebuildDerivedAndRender()
    }

    fun applyRemovedRecords(unavailable: List<Track>) {
        applyMaintenance(emptyList(), unavailable)
    }
}
