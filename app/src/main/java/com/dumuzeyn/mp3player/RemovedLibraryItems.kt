package com.dumuzeyn.mp3player

/** Tracks and permissions affected by one committed library mutation. */
class RemovedLibraryItems {
    @JvmField val trackIds: MutableSet<String> = HashSet()
    @JvmField val trackUris: MutableSet<String> = HashSet()
    @JvmField val sources: MutableList<LibrarySource> = ArrayList()
    @JvmField var clearQueue: Boolean = false

    fun add(trackId: String, uri: String) {
        trackIds += trackId
        trackUris += uri
    }
}
