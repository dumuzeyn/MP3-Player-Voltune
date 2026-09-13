package com.dumuzeyn.mp3player

/** Indexed in-memory access to the library and collection persistence boundary. */
class LibraryRepository(
    private val tracks: List<Track>,
    private val favorites: MutableSet<String>,
    private val playlists: List<Playlist>,
    private val persistence: Persistence,
) {
    fun interface Persistence {
        fun save(favorites: Set<String>, playlists: List<Playlist>)
    }

    private val tracksByUri = HashMap<String, Track>()
    private val tracksById = HashMap<String, Track>()

    init {
        reindex()
    }

    fun reindex() {
        tracksByUri.clear()
        tracksById.clear()
        tracks.forEach { track ->
            tracksByUri[track.uri] = track
            tracksById[track.trackId] = track
        }
    }

    fun find(uriOrId: String?): Track? {
        if (uriOrId.isNullOrEmpty()) return null
        return tracksByUri[uriOrId] ?: tracksById[uriOrId]
    }

    fun toggleFavorite(track: Track?): Boolean {
        if (track == null) return false
        val favorite = if (favorites.remove(track.uri)) {
            false
        } else {
            favorites += track.uri
            true
        }
        persistCollections()
        return favorite
    }

    fun persistCollections() = persistence.save(favorites, playlists)
}
