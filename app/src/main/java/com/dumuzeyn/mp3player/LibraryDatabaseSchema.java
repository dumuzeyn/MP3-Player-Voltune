package com.dumuzeyn.mp3player;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashMap;
import java.util.Map;

final class LibraryDatabaseSchema {
    private LibraryDatabaseSchema() {
    }

    static void createLatest(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, "
                + "uri TEXT UNIQUE NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, "
                + "album TEXT NOT NULL, album_artist TEXT NOT NULL DEFAULT '', "
                + "genre TEXT NOT NULL, year INTEGER NOT NULL DEFAULT 0, "
                + "track_number INTEGER NOT NULL DEFAULT 0, disc_number INTEGER NOT NULL DEFAULT 0, "
                + "duration_ms INTEGER NOT NULL DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, "
                + "last_modified INTEGER NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', "
                + "availability_reason TEXT NOT NULL DEFAULT '', metadata_revision INTEGER NOT NULL DEFAULT 0, "
                + "play_count INTEGER NOT NULL DEFAULT 0, "
                + "skip_count INTEGER NOT NULL DEFAULT 0, date_added INTEGER NOT NULL DEFAULT 0, "
                + "last_played_at INTEGER NOT NULL DEFAULT 0, last_completed_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX index_tracks_last_played ON tracks(last_played_at DESC)");
        db.execSQL("CREATE INDEX index_tracks_play_count ON tracks(play_count DESC)");
        db.execSQL("CREATE INDEX index_tracks_date_added ON tracks(date_added DESC)");
        db.execSQL("CREATE TABLE favorites (track_id TEXT PRIMARY KEY NOT NULL)");
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, position INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, "
                + "track_id TEXT NOT NULL, position INTEGER NOT NULL, "
                + "PRIMARY KEY (playlist_id, track_id))");
    }

    static void migrate(SQLiteDatabase db, int oldVersion, int newVersion) {
        int version = oldVersion;
        if (version < 2 && newVersion >= 2) {
            migrateVersion1To2(db);
            version = 2;
        }
        if (version < 3 && newVersion >= 3) {
            migrateVersion2To3(db);
            version = 3;
        }
        if (version < 4 && newVersion >= 4) {
            addColumn(db, "metadata_revision INTEGER NOT NULL DEFAULT 0");
        }
    }

    private static void migrateVersion2To3(SQLiteDatabase db) {
        addColumn(db, "album_artist TEXT NOT NULL DEFAULT ''");
        addColumn(db, "year INTEGER NOT NULL DEFAULT 0");
        addColumn(db, "track_number INTEGER NOT NULL DEFAULT 0");
        addColumn(db, "disc_number INTEGER NOT NULL DEFAULT 0");
        addColumn(db, "play_count INTEGER NOT NULL DEFAULT 0");
        addColumn(db, "skip_count INTEGER NOT NULL DEFAULT 0");
        addColumn(db, "date_added INTEGER NOT NULL DEFAULT 0");
        addColumn(db, "last_played_at INTEGER NOT NULL DEFAULT 0");
        addColumn(db, "last_completed_at INTEGER NOT NULL DEFAULT 0");
        db.execSQL("UPDATE tracks SET album_artist=artist WHERE album_artist='' ");
        db.execSQL("UPDATE tracks SET date_added=CASE WHEN last_modified>0 THEN last_modified "
                + "ELSE strftime('%s','now') * 1000 END WHERE date_added=0");
        db.execSQL("CREATE INDEX index_tracks_last_played ON tracks(last_played_at DESC)");
        db.execSQL("CREATE INDEX index_tracks_play_count ON tracks(play_count DESC)");
        db.execSQL("CREATE INDEX index_tracks_date_added ON tracks(date_added DESC)");
    }

    private static void addColumn(SQLiteDatabase db, String definition) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN " + definition);
    }

    private static void migrateVersion1To2(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE tracks RENAME TO tracks_v1");
        db.execSQL("ALTER TABLE favorites RENAME TO favorites_v1");
        db.execSQL("ALTER TABLE playlist_tracks RENAME TO playlist_tracks_v1");
        createVersion2Tracks(db);
        Map<String, String> ids = copyTracks(db);
        copyFavorites(db, ids);
        copyPlaylistTracks(db, ids);
        db.execSQL("DROP TABLE tracks_v1");
        db.execSQL("DROP TABLE favorites_v1");
        db.execSQL("DROP TABLE playlist_tracks_v1");
    }

    private static void createVersion2Tracks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, "
                + "uri TEXT UNIQUE NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, "
                + "album TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL "
                + "DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, last_modified INTEGER "
                + "NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', "
                + "availability_reason TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE favorites (track_id TEXT PRIMARY KEY NOT NULL)");
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, "
                + "track_id TEXT NOT NULL, position INTEGER NOT NULL, "
                + "PRIMARY KEY (playlist_id, track_id))");
    }

    private static Map<String, String> copyTracks(SQLiteDatabase db) {
        Map<String, String> ids = new HashMap<>();
        Cursor tracks = db.query("tracks_v1", null, null, null, null, null, null);
        try {
            while (tracks.moveToNext()) {
                String uri = tracks.getString(tracks.getColumnIndexOrThrow("uri"));
                String trackId = TrackIdentity.fromLegacyUri(uri);
                ids.put(uri, trackId);
                ContentValues values = new ContentValues();
                values.put("track_id", trackId);
                values.put("uri", uri);
                copyText(tracks, values, "title");
                copyText(tracks, values, "artist");
                copyText(tracks, values, "album");
                copyText(tracks, values, "genre");
                values.put("duration_ms", tracks.getInt(
                        tracks.getColumnIndexOrThrow("duration_ms")));
                db.insertOrThrow("tracks", null, values);
            }
        } finally {
            tracks.close();
        }
        return ids;
    }

    private static void copyText(Cursor source, ContentValues target, String column) {
        target.put(column, source.getString(source.getColumnIndexOrThrow(column)));
    }

    private static void copyFavorites(SQLiteDatabase db, Map<String, String> ids) {
        Cursor cursor = db.query("favorites_v1", new String[]{"uri"}, null, null,
                null, null, null);
        try {
            while (cursor.moveToNext()) {
                String trackId = ids.get(cursor.getString(0));
                if (trackId != null) {
                    ContentValues values = new ContentValues();
                    values.put("track_id", trackId);
                    db.insertOrThrow("favorites", null, values);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private static void copyPlaylistTracks(SQLiteDatabase db, Map<String, String> ids) {
        Cursor cursor = db.query("playlist_tracks_v1",
                new String[]{"playlist_id", "uri", "position"}, null, null, null, null,
                null);
        try {
            while (cursor.moveToNext()) {
                String trackId = ids.get(cursor.getString(1));
                if (trackId != null) {
                    ContentValues values = new ContentValues();
                    values.put("playlist_id", cursor.getLong(0));
                    values.put("track_id", trackId);
                    values.put("position", cursor.getInt(2));
                    db.insertOrThrow("playlist_tracks", null, values);
                }
            }
        } finally {
            cursor.close();
        }
    }
}
