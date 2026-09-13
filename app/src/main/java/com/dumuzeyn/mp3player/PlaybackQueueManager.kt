package com.dumuzeyn.mp3player

class PlaybackQueueManager {
    private val tracks = ArrayList<Track>()

    fun replace(source: List<Track>) {
        tracks.clear()
        tracks.addAll(source)
    }

    fun rebuild(library: List<Track>, queueUris: List<String>) {
        replace(PlaybackQueueResolver.restore(library, queueUris, null))
    }

    fun isEmpty(): Boolean = tracks.isEmpty()
    fun size(): Int = tracks.size
    operator fun get(index: Int): Track = tracks[index]
    fun tracks(): List<Track> = tracks

    fun normalizeIndex(index: Int): Int =
        if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)

    fun indexOfUri(uri: String?): Int =
        if (uri.isNullOrEmpty()) -1 else tracks.indexOfFirst { it.uri == uri }

    fun uriAt(index: Int): String = tracks.getOrNull(index)?.uri.orEmpty()
}
