package com.dumuzeyn.mp3player

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** SQLite source of truth for tracks, collections, statistics, and library migrations. */
class LibraryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext ?: context,
    DB_NAME,
    null,
    DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) = LibraryDatabaseSchema.createLatest(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        LibraryDatabaseSchema.migrate(db, oldVersion, newVersion)

    fun loadTracks(): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        try {
            readableDatabase.query(
                "tracks",
                null,
                null,
                null,
                null,
                null,
                "title COLLATE NOCASE ASC",
            ).use { cursor ->
                while (cursor.moveToNext()) tracks += trackFromCursor(cursor)
            }
        } catch (error: Exception) {
            VoltuneLog.failure("sqlite_track_load_failed", error)
        }
        return tracks
    }

    fun loadTracksNeedingMetadataRefresh(revision: Int): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        readableDatabase.query(
            "tracks",
            null,
            "metadata_revision<?",
            arrayOf(revision.toString()),
            null,
            null,
            "title COLLATE NOCASE ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) tracks += trackFromCursor(cursor)
        }
        return tracks
    }

    fun saveTracks(tracks: List<Track>) = writableDatabase.transaction {
        saveTracks(this, tracks)
    }

    fun updateDuration(uri: String, durationMs: Int) {
        val values = ContentValues().apply { put("duration_ms", durationMs) }
        writableDatabase.update("tracks", values, "uri=?", arrayOf(uri))
    }

    fun upsertTrack(track: Track) {
        val values = trackValues(track).apply {
            put("metadata_revision", LibraryMaintenanceController.METADATA_REVISION)
        }
        writableDatabase.insertWithOnConflict(
            "tracks",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteTrack(trackId: String) = writableDatabase.transaction {
        val args = arrayOf(trackId)
        delete("favorites", "track_id=?", args)
        delete("playlist_tracks", "track_id=?", args)
        delete("track_sources", "track_id=?", args)
        delete("tracks", "track_id=?", args)
    }

    fun updateTrackMetadata(track: Track) {
        val values = trackValues(track).apply {
            remove("track_id")
            put("metadata_revision", LibraryMaintenanceController.METADATA_REVISION)
        }
        writableDatabase.update("tracks", values, "track_id=?", arrayOf(track.trackId))
    }

    fun updateTrackMetadata(tracks: List<Track>) = writableDatabase.transaction {
        tracks.forEach { track ->
            val values = trackValues(track).apply {
                remove("track_id")
                put("metadata_revision", LibraryMaintenanceController.METADATA_REVISION)
            }
            update("tracks", values, "track_id=?", arrayOf(track.trackId))
        }
    }

    fun updateTrackLocation(track: Track) {
        val values = ContentValues().apply {
            put("uri", track.uri)
            put("file_size", track.fileSize)
            put("last_modified", track.lastModified)
            put("fingerprint", track.fingerprint)
            put("availability_reason", "")
        }
        writableDatabase.update("tracks", values, "track_id=?", arrayOf(track.trackId))
    }

    fun updateAvailability(trackId: String, reason: String?) {
        val values = ContentValues().apply { put("availability_reason", reason.orEmpty()) }
        writableDatabase.update("tracks", values, "track_id=?", arrayOf(trackId))
    }

    fun applyMaintenance(
        refreshed: List<Track>,
        checkedTrackIds: Set<String>,
        unavailable: List<Track>,
        revision: Int,
    ) = writableDatabase.transaction {
        refreshed.forEach { track ->
            val values = trackValues(track).apply {
                remove("track_id")
                put("metadata_revision", revision)
            }
            update("tracks", values, "track_id=?", arrayOf(track.trackId))
        }
        val checked = ContentValues().apply { put("metadata_revision", revision) }
        checkedTrackIds.forEach { trackId ->
            update("tracks", checked, "track_id=?", arrayOf(trackId))
        }
        unavailable.forEach { track ->
            val args = arrayOf(track.trackId)
            delete("favorites", "track_id=?", args)
            delete("playlist_tracks", "track_id=?", args)
            delete("track_sources", "track_id=?", args)
            delete("tracks", "track_id=?", args)
        }
    }

    fun recordPlayed(trackId: String, completed: Boolean, timestamp: Long) =
        writableDatabase.transaction {
            val safeTimestamp = timestamp.coerceAtLeast(0L)
            if (!completed) {
                execSQL(
                    "UPDATE tracks SET play_count=play_count+1, last_played_at=? WHERE track_id=?",
                    arrayOf<Any>(timestamp, trackId),
                )
            } else {
                val values = ContentValues().apply {
                    put("last_played_at", safeTimestamp)
                    put("last_completed_at", safeTimestamp)
                }
                update("tracks", values, "track_id=?", arrayOf(trackId))
            }
        }

    @Suppress("UNUSED_PARAMETER")
    fun recordSkipped(trackId: String, timestamp: Long) {
        writableDatabase.execSQL(
            "UPDATE tracks SET skip_count=skip_count+1 WHERE track_id=?",
            arrayOf(trackId),
        )
    }

    fun loadFavorites(): HashSet<String> {
        val favorites = HashSet<String>()
        readableDatabase.rawQuery(
            "SELECT tracks.uri FROM favorites JOIN tracks " +
                "ON tracks.track_id=favorites.track_id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) favorites += cursor.getString(0)
        }
        return favorites
    }

    fun saveFavorites(favorites: Set<String>) = writableDatabase.transaction {
        saveFavorites(this, favorites)
    }

    fun loadPlaylists(): ArrayList<Playlist> {
        val grouped = LinkedHashMap<Long, Playlist>()
        readableDatabase.rawQuery(
            "SELECT playlists.id, playlists.name, tracks.uri " +
                "FROM playlists LEFT JOIN playlist_tracks " +
                "ON playlist_tracks.playlist_id=playlists.id LEFT JOIN tracks " +
                "ON tracks.track_id=playlist_tracks.track_id " +
                "ORDER BY playlists.position ASC, playlists.id ASC, " +
                "playlist_tracks.position ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val playlist = grouped.getOrPut(cursor.getLong(0)) {
                    Playlist(cursor.getString(1))
                }
                if (!cursor.isNull(2)) playlist.uris += cursor.getString(2)
            }
        }
        return ArrayList(grouped.values)
    }

    fun savePlaylists(playlists: List<Playlist>) = writableDatabase.transaction {
        savePlaylists(this, playlists)
    }

    fun saveCollections(favorites: Set<String>, playlists: List<Playlist>) =
        writableDatabase.transaction {
            saveFavorites(this, favorites)
            savePlaylists(this, playlists)
        }

    private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        try {
            val result = block()
            setTransactionSuccessful()
            return result
        } finally {
            endTransaction()
        }
    }

    companion object {
        const val DB_NAME = "mp3_player_library.db"
        const val DB_VERSION = 7
        private const val PREFS_STORE = "mp3_player_store"
        private const val PREFS_UI = "mp3_player_ui"
        private const val PREFS_MIGRATED = "sqlite_migrated"

        @JvmStatic
        fun migrateLegacyIfNeeded(context: Context) {
            val storePrefs = context.getSharedPreferences(PREFS_STORE, Context.MODE_PRIVATE)
            if (storePrefs.getBoolean(PREFS_MIGRATED, false)) return
            val database = LibraryDatabase(context)
            val db = database.writableDatabase
            try {
                db.beginTransaction()
                if (countRows(db, "tracks") == 0L) {
                    saveTracks(db, TrackStore.loadFromJson(storePrefs.getString("tracks", "[]")))
                }
                val uiPrefs = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
                if (countRows(db, "favorites") == 0L) {
                    saveFavorites(db, uiPrefs.getStringSet("favorites", emptySet()).orEmpty())
                }
                if (countRows(db, "playlists") == 0L) {
                    savePlaylists(
                        db,
                        PlaylistManager.fromJson(uiPrefs.getString("playlists", "[]")),
                    )
                }
                db.setTransactionSuccessful()
                storePrefs.edit().putBoolean(PREFS_MIGRATED, true).apply()
            } catch (error: Exception) {
                VoltuneLog.failure("sqlite_migration_failed", error)
            } finally {
                db.endTransaction()
                database.close()
            }
        }

        private fun saveTracks(db: SQLiteDatabase, tracks: List<Track>) {
            val storedById = HashMap<String, Track>()
            db.query("tracks", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val stored = trackFromCursor(cursor)
                    storedById[stored.trackId] = stored
                }
            }
            tracks.forEach { track ->
                val stored = storedById.remove(track.trackId)
                when {
                    stored == null -> db.insertWithOnConflict(
                        "tracks",
                        null,
                        trackValues(track),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                    !sameStoredTrack(stored, track) -> db.update(
                        "tracks",
                        trackValues(track),
                        "track_id=?",
                        arrayOf(track.trackId),
                    )
                }
            }
            storedById.keys.forEach { trackId ->
                val args = arrayOf(trackId)
                db.delete("favorites", "track_id=?", args)
                db.delete("playlist_tracks", "track_id=?", args)
                db.delete("track_sources", "track_id=?", args)
                db.delete("tracks", "track_id=?", args)
            }
        }

        private fun sameStoredTrack(left: Track, right: Track): Boolean =
            left.durationMs == right.durationMs &&
                left.fileSize == right.fileSize &&
                left.lastModified == right.lastModified &&
                left.year == right.year &&
                left.trackNumber == right.trackNumber &&
                left.discNumber == right.discNumber &&
                left.playCount == right.playCount &&
                left.skipCount == right.skipCount &&
                left.dateAdded == right.dateAdded &&
                left.lastPlayedAt == right.lastPlayedAt &&
                left.lastCompletedAt == right.lastCompletedAt &&
                left.uri == right.uri &&
                left.title == right.title &&
                left.artist == right.artist &&
                left.album == right.album &&
                left.albumArtist == right.albumArtist &&
                left.genre == right.genre &&
                left.fingerprint == right.fingerprint

        @JvmStatic
        fun trackValues(track: Track): ContentValues = ContentValues().apply {
            put("track_id", track.trackId)
            put("uri", track.uri)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("album_artist", track.albumArtist)
            put("genre", track.genre)
            put("year", track.year)
            put("track_number", track.trackNumber)
            put("disc_number", track.discNumber)
            put("duration_ms", track.durationMs)
            put("file_size", track.fileSize)
            put("last_modified", track.lastModified)
            put("fingerprint", track.fingerprint)
            put("availability_reason", "")
            put("play_count", track.playCount)
            put("skip_count", track.skipCount)
            put("date_added", track.dateAdded)
            put("last_played_at", track.lastPlayedAt)
            put("last_completed_at", track.lastCompletedAt)
        }

        @JvmStatic
        fun trackFromCursor(cursor: Cursor): Track = Track(
            cursor.getString(cursor.getColumnIndexOrThrow("track_id")),
            cursor.getString(cursor.getColumnIndexOrThrow("uri")),
            cursor.getString(cursor.getColumnIndexOrThrow("title")),
            cursor.getString(cursor.getColumnIndexOrThrow("artist")),
            cursor.getString(cursor.getColumnIndexOrThrow("album")),
            cursor.getString(cursor.getColumnIndexOrThrow("album_artist")),
            cursor.getString(cursor.getColumnIndexOrThrow("genre")),
            cursor.getInt(cursor.getColumnIndexOrThrow("year")),
            cursor.getInt(cursor.getColumnIndexOrThrow("track_number")),
            cursor.getInt(cursor.getColumnIndexOrThrow("disc_number")),
            cursor.getInt(cursor.getColumnIndexOrThrow("duration_ms")),
            cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
            cursor.getLong(cursor.getColumnIndexOrThrow("last_modified")),
            cursor.getString(cursor.getColumnIndexOrThrow("fingerprint")),
            cursor.getInt(cursor.getColumnIndexOrThrow("play_count")),
            cursor.getInt(cursor.getColumnIndexOrThrow("skip_count")),
            cursor.getLong(cursor.getColumnIndexOrThrow("date_added")),
            cursor.getLong(cursor.getColumnIndexOrThrow("last_played_at")),
            cursor.getLong(cursor.getColumnIndexOrThrow("last_completed_at")),
        )

        private fun idsByUri(db: SQLiteDatabase): Map<String, String> {
            val ids = HashMap<String, String>()
            db.query("tracks", arrayOf("uri", "track_id"), null, null, null, null, null)
                .use { cursor ->
                    while (cursor.moveToNext()) ids[cursor.getString(0)] = cursor.getString(1)
                }
            return ids
        }

        private fun saveFavorites(db: SQLiteDatabase, favorites: Set<String>) {
            val ids = idsByUri(db)
            db.delete("favorites", null, null)
            favorites.forEach { uri ->
                val trackId = ids[uri] ?: return@forEach
                val values = ContentValues().apply { put("track_id", trackId) }
                db.insertWithOnConflict(
                    "favorites",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }

        private fun savePlaylists(db: SQLiteDatabase, playlists: List<Playlist>) {
            val ids = idsByUri(db)
            db.delete("playlist_tracks", null, null)
            db.delete("playlists", null, null)
            playlists.forEachIndexed { index, playlist ->
                val values = ContentValues().apply {
                    put("name", PlaylistManager.cleanName(playlist.name))
                    put("position", index)
                }
                val playlistId = db.insert("playlists", null, values)
                playlist.uris.forEachIndexed { songIndex, uri ->
                    val trackId = ids[uri] ?: return@forEachIndexed
                    val song = ContentValues().apply {
                        put("playlist_id", playlistId)
                        put("track_id", trackId)
                        put("position", songIndex)
                    }
                    db.insertWithOnConflict(
                        "playlist_tracks",
                        null,
                        song,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
        }

        private fun countRows(db: SQLiteDatabase, table: String): Long =
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
    }
}
