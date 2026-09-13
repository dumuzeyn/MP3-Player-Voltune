package com.dumuzeyn.mp3player

class LibraryListController(private val host: MainActivityCore) {
    fun currentVisibleTracks(): ArrayList<Track> = filter(
        if (host.navigationState.tabIndex == LibraryTabs.FAVORITES) {
            favoriteTracks()
        } else {
            host.libraryState.tracks
        },
    )

    fun favoriteTracks(): ArrayList<Track> = ArrayList(host.libraryState.homeContent.allFavorites)

    fun filter(source: ArrayList<Track>): ArrayList<Track> {
        if (host.navigationState.search.isBlank()) return source
        val query = Track.normalizeSearchText(host.navigationState.search)
        return source.filterTo(ArrayList()) { matchesTrackSearch(it, query) }
    }

    fun matchesTrackSearch(track: Track?, query: String?): Boolean =
        track != null && query.orEmpty() in track.normalizedSearchText

    fun containsSearch(value: String?, query: String?): Boolean =
        Track.normalizeSearchText(query) in Track.normalizeSearchText(value)
}
