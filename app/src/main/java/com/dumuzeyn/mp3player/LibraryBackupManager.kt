package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

internal object LibraryBackupManager {
    const val MAX_BACKUP_BYTES = 1024 * 1024
    private const val SCHEMA_VERSION = 1
    private const val UI_PREFS = "mp3_player_ui"

    @JvmStatic
    fun exportBackup(context: Context, tracks: List<Track>, playlists: List<Playlist>): String {
        val idsByUri = tracks.associate { it.uri to it.trackId }
        val playlistArray = JSONArray()
        playlists.forEach { playlist ->
            val ids = JSONArray()
            playlist.uris.forEach { uri -> idsByUri[uri]?.let(ids::put) }
            playlistArray.put(
                JSONObject()
                    .put("name", PlaylistManager.cleanName(playlist.name))
                    .put("trackIds", ids),
            )
        }
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("playlists", playlistArray)
            .put(
                "settings",
                encodeSettings(context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)),
            )
            .put(
                "settingTypes",
                encodeSettingTypes(context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)),
            )
        val encoded = root.toString(2)
        if (encoded.toByteArray(StandardCharsets.UTF_8).size > MAX_BACKUP_BYTES) {
            throw IllegalArgumentException("Backup is too large")
        }
        return encoded
    }

    @JvmStatic
    fun importBackup(context: Context, encoded: String?, tracks: List<Track>): ImportResult {
        if (encoded == null || encoded.toByteArray(StandardCharsets.UTF_8).size > MAX_BACKUP_BYTES) {
            throw IllegalArgumentException("Backup is empty or too large")
        }
        val root = JSONObject(encoded)
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup schema")
        }
        val urisById = tracks.associate { it.trackId to it.uri }
        val playlists = ArrayList<Playlist>()
        root.optJSONArray("playlists")?.let { playlistArray ->
            for (index in 0 until minOf(playlistArray.length(), 1_000)) {
                val item = playlistArray.getJSONObject(index)
                val playlist = Playlist(
                    PlaylistManager.cleanName(item.optString("name", "Playlist")),
                )
                item.optJSONArray("trackIds")?.let { ids ->
                    for (songIndex in 0 until minOf(ids.length(), 10_000)) {
                        val uri = urisById[ids.optString(songIndex, "")]
                        if (uri != null && !playlist.uris.contains(uri)) playlist.uris.add(uri)
                    }
                }
                playlists.add(playlist)
            }
        }
        restoreSettings(
            context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE),
            root.optJSONObject("settings"),
            root.optJSONObject("settingTypes"),
        )
        return ImportResult(playlists)
    }

    private fun encodeSettings(preferences: SharedPreferences): JSONObject {
        val result = JSONObject()
        preferences.all.forEach { (key, value) ->
            when (value) {
                is Boolean, is Int, is Long, is Float, is String -> result.put(key, value)
                is Set<*> -> {
                    val values = JSONArray()
                    value.forEach { item -> if (item is String) values.put(item) }
                    result.put(key, values)
                }
            }
        }
        return result
    }

    private fun encodeSettingTypes(preferences: SharedPreferences): JSONObject {
        val result = JSONObject()
        preferences.all.forEach { (key, value) ->
            val type = when (value) {
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                is String -> "string"
                is Set<*> -> "stringSet"
                else -> null
            }
            if (type != null) result.put(key, type)
        }
        return result
    }

    private fun restoreSettings(
        preferences: SharedPreferences,
        settings: JSONObject?,
        settingTypes: JSONObject?,
    ) {
        if (settings == null || settings.length() > 300) return
        val editor = preferences.edit()
        val names = settings.names() ?: return
        for (index in 0 until names.length()) {
            val key = names.getString(index)
            val value = settings.get(key)
            when (settingTypes?.optString(key)) {
                "boolean" -> if (value is Boolean) editor.putBoolean(key, value)
                "int" -> if (value is Number) editor.putInt(key, value.toInt())
                "long" -> if (value is Number) editor.putLong(key, value.toLong())
                "float" -> if (value is Number) editor.putFloat(key, value.toFloat())
                "string" -> if (value is String && value.length <= 4_096) {
                    editor.putString(key, value)
                }
                "stringSet" -> if (value is JSONArray) {
                    editor.putStringSet(key, decodeStringSet(value))
                }
                else -> restoreLegacySetting(editor, key, value)
            }
        }
        editor.commit()
    }

    private fun restoreLegacySetting(
        editor: SharedPreferences.Editor,
        key: String,
        value: Any,
    ) {
        when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> if (
                    value >= Int.MIN_VALUE && value <= Int.MAX_VALUE && value == Math.rint(value)
                ) {
                    editor.putInt(key, value.toInt())
                } else {
                    editor.putFloat(key, value.toFloat())
                }
                is String -> if (value.length <= 4_096) editor.putString(key, value)
                is JSONArray -> editor.putStringSet(key, decodeStringSet(value))
            }
    }

    private fun decodeStringSet(array: JSONArray): Set<String> {
        val values = HashSet<String>()
        for (itemIndex in 0 until minOf(array.length(), 1_000)) {
            val item = array.optString(itemIndex, "")
            if (item.length <= 4_096) values.add(item)
        }
        return values
    }

    class ImportResult(@JvmField val playlists: ArrayList<Playlist>)
}
