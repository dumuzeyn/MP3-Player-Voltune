package com.dumuzeyn.mp3player

class HomeContentBuilder {
    private val smartResolver = SmartPlaylistResolver()

    fun build(tracks: List<Track>, favorites: Set<String>, playlists: List<Playlist>): HomeContent {
        val now = System.currentTimeMillis()
        val favoriteTracks = tracks
            .filterTo(ArrayList()) { it.uri in favorites }
            .apply { sortByDescending(Track::playCount) }
        val groups = Groups(tracks)
        return HomeContent(
            smart(SmartPlaylistDefinition.RECENTLY_PLAYED, tracks, favorites, now),
            smart(SmartPlaylistDefinition.RECENTLY_ADDED, tracks, favorites, now),
            smart(SmartPlaylistDefinition.MOST_PLAYED, tracks, favorites, now),
            favoriteTracks.take(SECTION_LIMIT),
            favoriteTracks,
            playlists.takeLast(SECTION_LIMIT).asReversed(),
            popularGroups(groups.artists),
            popularGroups(groups.albums),
            FolderGrouping().group(tracks),
            groups.artists,
            groups.albums,
            groups.genres,
        )
    }

    private fun smart(
        definition: SmartPlaylistDefinition,
        tracks: List<Track>,
        favorites: Set<String>,
        now: Long,
    ): List<Track> = smartResolver.resolve(definition, tracks, favorites, now, SECTION_LIMIT)

    private fun popularGroups(groups: Map<String, ArrayList<Track>>): List<String> = groups.entries
        .sortedByDescending { it.value.size }
        .asSequence()
        .map { it.key }
        .filter(String::isNotEmpty)
        .take(SECTION_LIMIT)
        .toList()

    private class Groups(tracks: List<Track>) {
        val artists = LinkedHashMap<String, ArrayList<Track>>()
        val albums = LinkedHashMap<String, ArrayList<Track>>()
        val genres = LinkedHashMap<String, ArrayList<Track>>()

        init {
            tracks.forEach { track ->
                add(artists, groupName(track.artist, "Unknown artist"), track)
                add(albums, groupName(track.album, "Unknown album"), track)
                add(genres, if (GenreNormalizer.isUnknown(track.genre)) "" else track.genre.trim(), track)
            }
        }

        private fun groupName(value: String?, placeholder: String): String =
            value?.trim()?.takeUnless {
                it.isEmpty() || it.equals(placeholder, ignoreCase = true)
            }.orEmpty()

        private fun add(target: MutableMap<String, ArrayList<Track>>, name: String, track: Track) {
            target.getOrPut(name, ::ArrayList) += track
        }
    }

    companion object {
        private const val SECTION_LIMIT = 8
    }
}
