package com.dumuzeyn.mp3player

class SmartPlaylistResolver {
    fun resolve(
        definition: SmartPlaylistDefinition,
        library: List<Track>,
        favorites: Set<String>,
        now: Long,
        limit: Int,
    ): List<Track> {
        val result = library
            .filterTo(ArrayList()) { matches(definition, it, favorites, now) }
            .apply { sortWith(comparator(definition)) }
        return if (limit > 0 && result.size > limit) ArrayList(result.subList(0, limit)) else result
    }

    private fun matches(
        definition: SmartPlaylistDefinition,
        track: Track,
        favorites: Set<String>,
        now: Long,
    ): Boolean = when (definition) {
        SmartPlaylistDefinition.RECENTLY_PLAYED -> track.lastPlayedAt > 0L
        SmartPlaylistDefinition.MOST_PLAYED -> track.playCount > 0
        SmartPlaylistDefinition.RECENTLY_ADDED -> track.dateAdded > 0L
        SmartPlaylistDefinition.NOT_PLAYED_RECENTLY ->
            track.playCount > 0 && track.lastPlayedAt < now - STALE_AFTER_MS
        SmartPlaylistDefinition.NEVER_PLAYED -> track.playCount == 0
        SmartPlaylistDefinition.MOST_LOVED -> track.uri in favorites
    }

    private fun comparator(definition: SmartPlaylistDefinition): Comparator<Track> = when (definition) {
        SmartPlaylistDefinition.RECENTLY_ADDED -> compareByDescending(Track::dateAdded)
        SmartPlaylistDefinition.MOST_PLAYED,
        SmartPlaylistDefinition.MOST_LOVED,
        -> compareByDescending<Track> { it.playCount }.thenByDescending(Track::lastPlayedAt)
        else -> compareByDescending<Track> { it.lastPlayedAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER, Track::title)
    }

    companion object {
        private const val STALE_AFTER_MS = 30L * 24L * 60L * 60L * 1_000L
    }
}
