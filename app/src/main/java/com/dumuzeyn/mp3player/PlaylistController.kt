package com.dumuzeyn.mp3player

import android.view.View
import android.widget.Button
import java.util.Locale

internal class PlaylistController(private val host: MainActivityCore) {
    private val playbackBindings = ArrayList<PlaybackBinding>()
    private var playbackGeneration = -1

    fun beginPlaybackBindings(generation: Int) {
        if (playbackGeneration == generation) return
        playbackGeneration = generation
        playbackBindings.clear()
    }

    fun bindPlaybackState(
        playButton: Button,
        marker: View,
        tracks: ArrayList<Track>,
        generation: Int,
    ) {
        beginPlaybackBindings(generation)
        PlaybackBinding(playButton, marker, ArrayList(tracks), generation).also {
            playbackBindings.add(it)
            it.apply()
        }
    }

    fun refreshPlaybackState() {
        val iterator = playbackBindings.iterator()
        while (iterator.hasNext()) {
            val binding = iterator.next()
            if (!binding.isCurrentGeneration()) {
                iterator.remove()
            } else {
                binding.apply()
            }
        }
    }

    fun filteredPlaylists(query: String?): ArrayList<Playlist> {
        val normalized = query?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return host.libraryState.playlists.filterTo(ArrayList()) { playlist ->
            normalized.isEmpty() || host.containsSearch(playlist.name, normalized) ||
                playlistContainsSearch(playlist, normalized)
        }
    }

    fun playlistTracks(playlist: Playlist): ArrayList<Track> =
        playlist.uris.mapNotNullTo(ArrayList(), host::findTrack)

    fun sortedPlaylistTracks(playlist: Playlist): ArrayList<Track> =
        playlistTracks(playlist).apply {
            sortWith { left, right -> left.title.compareTo(right.title, ignoreCase = true) }
        }

    fun playlistContainsSearch(playlist: Playlist, query: String): Boolean =
        playlistTracks(playlist).any { host.matchesTrackSearch(it, query) }

    fun addTracksToPlaylist(playlist: Playlist, uris: Set<String>) {
        uris.forEach { uri ->
            if (!playlist.uris.contains(uri)) playlist.uris.add(uri)
        }
        saveAndRebuild()
    }

    fun addTrackToPlaylist(playlist: Playlist, track: Track) {
        if (!playlist.uris.contains(track.uri)) playlist.uris.add(track.uri)
        saveAndRebuild()
    }

    fun createPlaylist(rawName: String?): Playlist {
        val playlist = Playlist(cleanPlaylistName(rawName))
        host.libraryState.playlists.add(playlist)
        saveAndRebuild()
        return playlist
    }

    fun createPlaylistWithTrack(rawName: String?, track: Track): Playlist {
        val playlist = createPlaylist(rawName)
        if (!playlist.uris.contains(track.uri)) {
            playlist.uris.add(track.uri)
            saveAndRebuild()
        }
        return playlist
    }

    fun renamePlaylist(playlist: Playlist, rawName: String?) {
        playlist.name = cleanPlaylistName(rawName)
        saveAndRebuild()
    }

    fun deletePlaylist(playlist: Playlist) {
        host.libraryState.playlists.remove(playlist)
        saveAndRebuild()
    }

    fun removeTrackFromAllPlaylists(track: Track) {
        host.libraryState.playlists.forEach { it.uris.remove(track.uri) }
        saveAndRebuild()
    }

    private fun cleanPlaylistName(rawName: String?): String {
        val name = PlaylistManager.cleanName(rawName)
        return name.ifEmpty { host.tr("Playlist", "Плейлист") }
    }

    private fun saveAndRebuild() {
        host.saveLibraryState()
        host.librarySnapshotApplier.rebuildDerivedAndRender()
    }

    private inner class PlaybackBinding(
        private val playButton: Button,
        private val marker: View,
        private val tracks: ArrayList<Track>,
        private val generation: Int,
    ) {
        fun isCurrentGeneration(): Boolean =
            generation == playbackGeneration &&
                generation == host.navigationState.songRenderGeneration

        fun apply() {
            marker.visibility = if (host.playbackQueueController.isCurrentCollection(tracks)) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            SongRowStateRegistry.applyPlayState(
                playButton,
                host.playbackQueueController.isPlayingCollection(tracks),
            )
        }
    }
}
