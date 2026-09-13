package com.dumuzeyn.mp3player

/** Mutable library data owned by the activity graph. */
class LibraryState {
    @JvmField val tracks = ArrayList<Track>()
    @JvmField val favorites = HashSet<String>()
    @JvmField val playlists = ArrayList<Playlist>()
    @JvmField var homeContent: HomeContent = HomeContent.empty()
}
