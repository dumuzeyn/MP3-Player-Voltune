package com.dumuzeyn.mp3player

import org.json.JSONArray
import org.json.JSONObject

object PlaylistManager {
    private val CONTROL_CHARACTERS = Regex("""[\p{Cntrl}&&[^\n\t]]""")

    @JvmStatic
    fun fromJson(raw: String?): ArrayList<Playlist> {
        val playlists = ArrayList<Playlist>()
        try {
            val array = JSONArray(raw ?: "[]")
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val playlist = Playlist(cleanName(item.optString("name", "Playlist")))
                val songs = item.optJSONArray("songs")
                if (songs != null) {
                    for (songIndex in 0 until songs.length()) {
                        playlist.uris.add(songs.getString(songIndex))
                    }
                }
                playlists.add(playlist)
            }
        } catch (_: Exception) {
        }
        return playlists
    }

    @JvmStatic
    fun toJson(playlists: ArrayList<Playlist>): String {
        val array = JSONArray()
        playlists.forEach { playlist ->
            try {
                val songs = JSONArray()
                playlist.uris.forEach(songs::put)
                array.put(
                    JSONObject()
                        .put("name", playlist.name)
                        .put("songs", songs),
                )
            } catch (_: Exception) {
            }
        }
        return array.toString()
    }

    @JvmStatic
    fun cleanName(value: String?): String {
        val cleaned = value
            ?.replace(CONTROL_CHARACTERS, "")
            ?.replace('\n', ' ')
            ?.replace('\t', ' ')
            ?.trim()
            ?: return ""
        return if (cleaned.length > MAX_NAME_LENGTH) {
            cleaned.substring(0, MAX_NAME_LENGTH).trim()
        } else {
            cleaned
        }
    }

    private const val MAX_NAME_LENGTH = 80
}
