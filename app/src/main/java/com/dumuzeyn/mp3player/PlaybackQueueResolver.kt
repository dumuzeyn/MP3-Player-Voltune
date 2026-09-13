package com.dumuzeyn.mp3player

import org.json.JSONArray

object PlaybackQueueResolver {
    @JvmStatic
    fun restore(library: List<Track>, queueJson: String?, fallback: Track?): ArrayList<Track> {
        val savedIds = ArrayList<String>()
        runCatching {
            val savedQueue = JSONArray(queueJson ?: "[]")
            repeat(savedQueue.length()) { index ->
                savedQueue.optString(index, "").takeIf(String::isNotEmpty)?.let(savedIds::add)
            }
        }
        return restore(library, savedIds, fallback)
    }

    @JvmStatic
    fun restore(
        library: List<Track>,
        savedUris: List<String>,
        fallback: Track?,
    ): ArrayList<Track> {
        val tracksByIdentity = HashMap<String, Track>(library.size * 2)
        library.forEach { track ->
            tracksByIdentity[track.uri] = track
            tracksByIdentity[track.trackId] = track
        }
        return savedUris.mapNotNullTo(ArrayList()) { tracksByIdentity[it] }.also { restored ->
            if (restored.isEmpty() && fallback != null) restored += fallback
        }
    }
}
