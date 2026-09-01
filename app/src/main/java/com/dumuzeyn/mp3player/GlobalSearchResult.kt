package com.dumuzeyn.mp3player

import java.util.Collections

class GlobalSearchResult(
    songs: List<Track>,
    artists: List<String>,
    albums: List<String>,
    genres: List<String>,
    playlists: List<Playlist>,
) {
    @JvmField val songs = immutable(songs)
    @JvmField val artists = immutable(artists)
    @JvmField val albums = immutable(albums)
    @JvmField val genres = immutable(genres)
    @JvmField val playlists: List<Playlist> = Collections.unmodifiableList(ArrayList(playlists))

    companion object {
        @JvmStatic
        fun empty(): GlobalSearchResult =
            GlobalSearchResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        private fun <T> immutable(source: List<T>): List<T> =
            Collections.unmodifiableList(ArrayList(source))
    }
}
