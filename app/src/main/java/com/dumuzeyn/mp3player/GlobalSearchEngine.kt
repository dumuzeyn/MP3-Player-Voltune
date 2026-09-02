package com.dumuzeyn.mp3player

class GlobalSearchEngine {
    fun search(
        tracks: List<Track>,
        playlists: List<Playlist>,
        query: String?,
        categoryLimit: Int,
    ): GlobalSearchResult {
        val normalized = Track.normalizeSearchText(query)
        if (normalized.isEmpty()) return GlobalSearchResult.empty()
        val songs = ArrayList<Track>()
        val artists = LinkedHashMap<String, String>()
        val albums = LinkedHashMap<String, String>()
        val genres = LinkedHashMap<String, String>()
        val matchingPlaylists = ArrayList<Playlist>()
        tracks.forEach { track ->
            if (normalized in track.normalizedSearchText) addLimited(songs, track, categoryLimit)
            addGroup(artists, track.artist, normalized, categoryLimit)
            addGroup(albums, track.album, normalized, categoryLimit)
            addGroup(genres, track.genre, normalized, categoryLimit)
        }
        playlists.forEach { playlist ->
            if (normalized in Track.normalizeSearchText(playlist.name)) {
                addLimited(matchingPlaylists, playlist, categoryLimit)
            }
        }
        return GlobalSearchResult(
            songs,
            ArrayList(artists.values),
            ArrayList(albums.values),
            ArrayList(genres.values),
            matchingPlaylists,
        )
    }

    private fun addGroup(
        target: MutableMap<String, String>,
        value: String?,
        query: String,
        limit: Int,
    ) {
        val key = Track.normalizeSearchText(value)
        if (key.isNotEmpty() && query in key && (limit <= 0 || target.size < limit)) {
            target[key] = value.orEmpty()
        }
    }

    private fun <T> addLimited(target: MutableList<T>, value: T, limit: Int) {
        if (limit <= 0 || target.size < limit) target += value
    }
}
