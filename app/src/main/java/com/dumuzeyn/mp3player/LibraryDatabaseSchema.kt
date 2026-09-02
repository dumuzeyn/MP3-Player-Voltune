package com.dumuzeyn.mp3player

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** Creates the current database and preserves every historical upgrade path. */
object LibraryDatabaseSchema {
    @JvmStatic
    fun createLatest(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, " +
                "uri TEXT UNIQUE NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, " +
                "album TEXT NOT NULL, album_artist TEXT NOT NULL DEFAULT '', " +
                "genre TEXT NOT NULL, year INTEGER NOT NULL DEFAULT 0, " +
                "track_number INTEGER NOT NULL DEFAULT 0, disc_number INTEGER NOT NULL DEFAULT 0, " +
                "duration_ms INTEGER NOT NULL DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, " +
                "last_modified INTEGER NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', " +
                "availability_reason TEXT NOT NULL DEFAULT '', metadata_revision INTEGER NOT NULL DEFAULT 0, " +
                "play_count INTEGER NOT NULL DEFAULT 0, " +
                "skip_count INTEGER NOT NULL DEFAULT 0, date_added INTEGER NOT NULL DEFAULT 0, " +
                "last_played_at INTEGER NOT NULL DEFAULT 0, last_completed_at INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE INDEX index_tracks_last_played ON tracks(last_played_at DESC)")
        db.execSQL("CREATE INDEX index_tracks_play_count ON tracks(play_count DESC)")
        db.execSQL("CREATE INDEX index_tracks_date_added ON tracks(date_added DESC)")
        db.execSQL("CREATE TABLE favorites (track_id TEXT PRIMARY KEY NOT NULL)")
        db.execSQL(
            "CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, position INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, " +
                "track_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                "PRIMARY KEY (playlist_id, track_id))",
        )
        createLibrarySources(db)
        createSoundTables(db)
    }

    @JvmStatic
    fun migrate(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var version = oldVersion
        if (version < 2 && newVersion >= 2) {
            migrateVersion1To2(db)
            version = 2
        }
        if (version < 3 && newVersion >= 3) {
            migrateVersion2To3(db)
            version = 3
        }
        if (version < 4 && newVersion >= 4) {
            addColumn(db, "metadata_revision INTEGER NOT NULL DEFAULT 0")
            version = 4
        }
        if (version < 5 && newVersion >= 5) {
            createLibrarySources(db)
            version = 5
        }
        if (version < 6 && newVersion >= 6) {
            LibraryDuplicateCleaner.clean(db)
            version = 6
        }
        if (version < 7 && newVersion >= 7) createSoundTables(db)
    }

    private fun createSoundTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS audio_profiles (" +
                "track_id TEXT PRIMARY KEY NOT NULL, analysis_version INTEGER NOT NULL, " +
                "file_size INTEGER NOT NULL, last_modified INTEGER NOT NULL, " +
                "fingerprint TEXT NOT NULL, state TEXT NOT NULL, vector TEXT NOT NULL, " +
                "group_id TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '', " +
                "updated_at INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_audio_profiles_group " +
                "ON audio_profiles(group_id)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sound_groups (" +
                "group_id TEXT PRIMARY KEY NOT NULL, name_ru TEXT NOT NULL, " +
                "name_en TEXT NOT NULL, centroid TEXT NOT NULL, " +
                "position INTEGER NOT NULL, updated_at INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS delete_track_audio_profile " +
                "AFTER DELETE ON tracks BEGIN DELETE FROM audio_profiles " +
                "WHERE track_id=OLD.track_id; END",
        )
    }

    private fun createLibrarySources(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE library_sources (source_id TEXT PRIMARY KEY NOT NULL, " +
                "tree_uri TEXT UNIQUE NOT NULL, display_name TEXT NOT NULL, " +
                "revision INTEGER NOT NULL DEFAULT 1, added_at INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TABLE track_sources (track_id TEXT PRIMARY KEY NOT NULL, " +
                "source_id TEXT NOT NULL, document_id TEXT NOT NULL, " +
                "identity_key TEXT UNIQUE NOT NULL)",
        )
        db.execSQL("CREATE INDEX index_track_sources_source ON track_sources(source_id)")
        db.execSQL(
            "CREATE TABLE excluded_tracks (identity_key TEXT PRIMARY KEY NOT NULL, " +
                "source_id TEXT NOT NULL DEFAULT '', document_id TEXT NOT NULL DEFAULT '', " +
                "uri TEXT NOT NULL DEFAULT '', file_size INTEGER NOT NULL DEFAULT -1, " +
                "last_modified INTEGER NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', " +
                "removed_at INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE INDEX index_excluded_source ON excluded_tracks(source_id)")
        db.execSQL("CREATE INDEX index_excluded_fingerprint ON excluded_tracks(fingerprint)")
    }

    private fun migrateVersion2To3(db: SQLiteDatabase) {
        addColumn(db, "album_artist TEXT NOT NULL DEFAULT ''")
        addColumn(db, "year INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "track_number INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "disc_number INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "play_count INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "skip_count INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "date_added INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "last_played_at INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "last_completed_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE tracks SET album_artist=artist WHERE album_artist='' ")
        db.execSQL(
            "UPDATE tracks SET date_added=CASE WHEN last_modified>0 THEN last_modified " +
                "ELSE strftime('%s','now') * 1000 END WHERE date_added=0",
        )
        db.execSQL("CREATE INDEX index_tracks_last_played ON tracks(last_played_at DESC)")
        db.execSQL("CREATE INDEX index_tracks_play_count ON tracks(play_count DESC)")
        db.execSQL("CREATE INDEX index_tracks_date_added ON tracks(date_added DESC)")
    }

    private fun addColumn(db: SQLiteDatabase, definition: String) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN $definition")
    }

    private fun migrateVersion1To2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks RENAME TO tracks_v1")
        db.execSQL("ALTER TABLE favorites RENAME TO favorites_v1")
        db.execSQL("ALTER TABLE playlist_tracks RENAME TO playlist_tracks_v1")
        createVersion2Tracks(db)
        val ids = copyTracks(db)
        copyFavorites(db, ids)
        copyPlaylistTracks(db, ids)
        db.execSQL("DROP TABLE tracks_v1")
        db.execSQL("DROP TABLE favorites_v1")
        db.execSQL("DROP TABLE playlist_tracks_v1")
    }

    private fun createVersion2Tracks(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, " +
                "uri TEXT UNIQUE NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, " +
                "album TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL " +
                "DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, last_modified INTEGER " +
                "NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', " +
                "availability_reason TEXT NOT NULL DEFAULT '')",
        )
        db.execSQL("CREATE TABLE favorites (track_id TEXT PRIMARY KEY NOT NULL)")
        db.execSQL(
            "CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, " +
                "track_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                "PRIMARY KEY (playlist_id, track_id))",
        )
    }

    private fun copyTracks(db: SQLiteDatabase): Map<String, String> {
        val ids = HashMap<String, String>()
        db.query("tracks_v1", null, null, null, null, null, null).use { tracks ->
            while (tracks.moveToNext()) {
                val uri = tracks.getString(tracks.getColumnIndexOrThrow("uri"))
                val trackId = TrackIdentity.fromLegacyUri(uri)
                ids[uri] = trackId
                val values = ContentValues().apply {
                    put("track_id", trackId)
                    put("uri", uri)
                    copyText(tracks, this, "title")
                    copyText(tracks, this, "artist")
                    copyText(tracks, this, "album")
                    copyText(tracks, this, "genre")
                    put("duration_ms", tracks.getInt(tracks.getColumnIndexOrThrow("duration_ms")))
                }
                db.insertOrThrow("tracks", null, values)
            }
        }
        return ids
    }

    private fun copyText(source: Cursor, target: ContentValues, column: String) {
        target.put(column, source.getString(source.getColumnIndexOrThrow(column)))
    }

    private fun copyFavorites(db: SQLiteDatabase, ids: Map<String, String>) {
        db.query("favorites_v1", arrayOf("uri"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val trackId = ids[cursor.getString(0)] ?: continue
                db.insertOrThrow(
                    "favorites",
                    null,
                    ContentValues().apply { put("track_id", trackId) },
                )
            }
        }
    }

    private fun copyPlaylistTracks(db: SQLiteDatabase, ids: Map<String, String>) {
        db.query(
            "playlist_tracks_v1",
            arrayOf("playlist_id", "uri", "position"),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val trackId = ids[cursor.getString(1)] ?: continue
                val values = ContentValues().apply {
                    put("playlist_id", cursor.getLong(0))
                    put("track_id", trackId)
                    put("position", cursor.getInt(2))
                }
                db.insertOrThrow("playlist_tracks", null, values)
            }
        }
    }
}
