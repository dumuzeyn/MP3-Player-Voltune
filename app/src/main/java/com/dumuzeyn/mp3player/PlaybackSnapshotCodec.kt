package com.dumuzeyn.mp3player

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, path-free serialization used by persistence tests and diagnostics. */
object PlaybackSnapshotCodec {
    private const val VERSION = 1

    @JvmStatic
    fun encode(snapshot: PlaybackSnapshot): String = runCatching {
        JSONObject().apply {
            put("version", VERSION)
            put("queueMediaIds", JSONArray(snapshot.queueMediaIds))
            put("currentMediaId", snapshot.currentMediaId)
            put("currentIndex", snapshot.currentIndex)
            put("positionMs", snapshot.positionMs)
            put("durationMs", snapshot.durationMs)
            put("playWhenReady", snapshot.playWhenReady)
            put("playbackState", snapshot.playbackState)
            put("repeatMode", snapshot.repeatMode)
            put("shuffleEnabled", snapshot.shuffleEnabled)
            put("phase", snapshot.phase.name)
            put("pauseReason", snapshot.pauseReason.name)
            put("stopReason", snapshot.stopReason.name)
            put("updatedAt", snapshot.updatedAt)
        }.toString()
    }.getOrElse { throw IllegalStateException("Playback snapshot could not be encoded", it) }

    @JvmStatic
    fun decode(encoded: String?): PlaybackSnapshot = runCatching {
        val json = JSONObject(encoded ?: "{}")
        if (json.optInt("version", -1) != VERSION) return PlaybackSnapshot.empty()
        val items = json.optJSONArray("queueMediaIds")
        val queue = ArrayList<String>(items?.length() ?: 0)
        if (items != null) repeat(items.length()) { index ->
            items.optString(index, "").takeIf(String::isNotEmpty)?.let(queue::add)
        }
        PlaybackSnapshot(
            queue,
            json.optString("currentMediaId", ""),
            json.optInt("currentIndex", -1),
            json.optLong("positionMs", 0L),
            json.optLong("durationMs", 0L),
            json.optBoolean("playWhenReady", false),
            json.optInt("playbackState", 1),
            json.optInt("repeatMode", 0),
            json.optBoolean("shuffleEnabled", false),
            json.enumValue("phase", PlaybackPhase.IDLE),
            json.enumValue("pauseReason", PauseReason.NONE),
            json.enumValue("stopReason", StopReason.NONE),
            null,
            json.optLong("updatedAt", System.currentTimeMillis()),
        )
    }.getOrElse { PlaybackSnapshot.empty() }

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(optString(key)) }.getOrDefault(fallback)
}
