package com.dumuzeyn.mp3player

import java.util.Collections
import java.util.LinkedHashMap

class HomeContent(
    recentlyPlayed: List<Track>,
    recentlyAdded: List<Track>,
    mostPlayed: List<Track>,
    favorites: List<Track>,
    allFavorites: List<Track>,
    playlists: List<Playlist>,
    artists: List<String>,
    albums: List<String>,
    folders: Map<String, ArrayList<Track>>,
    artistTracks: Map<String, ArrayList<Track>>,
    albumTracks: Map<String, ArrayList<Track>>,
    genreTracks: Map<String, ArrayList<Track>>,
) {
    @JvmField val recentlyPlayed = immutable(recentlyPlayed)
    @JvmField val recentlyAdded = immutable(recentlyAdded)
    @JvmField val mostPlayed = immutable(mostPlayed)
    @JvmField val favorites = immutable(favorites)
    @JvmField val allFavorites = immutable(allFavorites)
    @JvmField val playlists: List<Playlist> = Collections.unmodifiableList(ArrayList(playlists))
    @JvmField val artists = immutable(artists)
    @JvmField val albums = immutable(albums)
    @JvmField val folders = immutableGroups(folders)
    @JvmField val artistTracks = immutableGroups(artistTracks)
    @JvmField val albumTracks = immutableGroups(albumTracks)
    @JvmField val genreTracks = immutableGroups(genreTracks)

    companion object {
        @JvmStatic
        fun empty(): HomeContent = HomeContent(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
        )

        private fun <T> immutable(source: List<T>): List<T> =
            Collections.unmodifiableList(ArrayList(source))

        private fun immutableGroups(
            source: Map<String, ArrayList<Track>>,
        ): Map<String, ArrayList<Track>> {
            val copy = LinkedHashMap<String, ArrayList<Track>>()
            source.forEach { (name, tracks) -> copy[name] = ArrayList(tracks) }
            return Collections.unmodifiableMap(copy)
        }
    }
}
