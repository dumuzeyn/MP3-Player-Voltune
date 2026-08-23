package com.dumuzeyn.mp3player;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LibraryDatabase extends SQLiteOpenHelper {
    static final String DB_NAME = "mp3_player_library.db";
    static final int DB_VERSION = 2;
    private static final String DEBUG_TAG = "VoltuneDebug";
    private static final String PREFS_STORE = "mp3_player_store";
    private static final String PREFS_UI = "mp3_player_ui";
    private static final String PREFS_MIGRATED = "sqlite_migrated";

    LibraryDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createVersion2(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            migrateVersion1To2(db);
        }
    }

    static void migrateLegacyIfNeeded(Context context) {
        SharedPreferences storePrefs = context.getSharedPreferences(PREFS_STORE,
                Context.MODE_PRIVATE);
        if (storePrefs.getBoolean(PREFS_MIGRATED, false)) {
            return;
        }
        LibraryDatabase database = new LibraryDatabase(context);
        SQLiteDatabase db = database.getWritableDatabase();
        try {
            db.beginTransaction();
            if (countRows(db, "tracks") == 0) {
                saveTracks(db, TrackStore.loadFromJson(storePrefs.getString("tracks", "[]")));
            }
            SharedPreferences uiPrefs = context.getSharedPreferences(PREFS_UI,
                    Context.MODE_PRIVATE);
            if (countRows(db, "favorites") == 0) {
                saveFavorites(db, uiPrefs.getStringSet("favorites", new HashSet<String>()));
            }
            if (countRows(db, "playlists") == 0) {
                savePlaylists(db,
                        PlaylistManager.fromJson(uiPrefs.getString("playlists", "[]")));
            }
            db.setTransactionSuccessful();
            storePrefs.edit().putBoolean(PREFS_MIGRATED, true).apply();
        } catch (Exception error) {
            Log.w(DEBUG_TAG, "sqlite_migration_failed error=" + error.getMessage());
        } finally {
            db.endTransaction();
            database.close();
        }
    }

    ArrayList<Track> loadTracks() {
        ArrayList<Track> tracks = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query("tracks", null, null, null, null, null,
                    "title COLLATE NOCASE ASC");
            while (cursor.moveToNext()) {
                tracks.add(trackFromCursor(cursor));
            }
        } catch (Exception error) {
            Log.w(DEBUG_TAG, "sqlite_track_load_failed error=" + error.getMessage());
        } finally {
            closeQuietly(cursor);
        }
        return tracks;
    }

    void saveTracks(List<Track> tracks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            saveTracks(db, tracks);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void updateDuration(String uri, int durationMs) {
        ContentValues values = new ContentValues();
        values.put("duration_ms", durationMs);
        getWritableDatabase().update("tracks", values, "uri=?", new String[]{uri});
    }

    void upsertTrack(Track track) {
        getWritableDatabase().insertWithOnConflict("tracks", null, trackValues(track),
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteTrack(String trackId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("favorites", "track_id=?", new String[]{trackId});
            db.delete("playlist_tracks", "track_id=?", new String[]{trackId});
            db.delete("tracks", "track_id=?", new String[]{trackId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void updateTrackMetadata(Track track) {
        ContentValues values = trackValues(track);
        values.remove("track_id");
        getWritableDatabase().update("tracks", values, "track_id=?",
                new String[]{track.trackId});
    }

    void updateTrackLocation(Track track) {
        ContentValues values = new ContentValues();
        values.put("uri", track.uri);
        values.put("file_size", track.fileSize);
        values.put("last_modified", track.lastModified);
        values.put("fingerprint", track.fingerprint);
        values.put("availability_reason", "");
        getWritableDatabase().update("tracks", values, "track_id=?",
                new String[]{track.trackId});
    }

    void updateAvailability(String trackId, String reason) {
        ContentValues values = new ContentValues();
        values.put("availability_reason", reason == null ? "" : reason);
        getWritableDatabase().update("tracks", values, "track_id=?", new String[]{trackId});
    }

    HashSet<String> loadFavorites() {
        HashSet<String> favorites = new HashSet<>();
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().rawQuery(
                    "SELECT tracks.uri FROM favorites JOIN tracks "
                            + "ON tracks.track_id=favorites.track_id", null);
            while (cursor.moveToNext()) {
                favorites.add(cursor.getString(0));
            }
        } finally {
            closeQuietly(cursor);
        }
        return favorites;
    }

    void saveFavorites(Set<String> favorites) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            saveFavorites(db, favorites);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    ArrayList<Playlist> loadPlaylists() {
        ArrayList<Playlist> playlists = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT playlists.id, playlists.name, tracks.uri "
                            + "FROM playlists "
                            + "LEFT JOIN playlist_tracks "
                            + "ON playlist_tracks.playlist_id=playlists.id "
                            + "LEFT JOIN tracks "
                            + "ON tracks.track_id=playlist_tracks.track_id "
                            + "ORDER BY playlists.position ASC, playlists.id ASC, "
                            + "playlist_tracks.position ASC",
                    null);
            LinkedHashMap<Long, Playlist> grouped = new LinkedHashMap<>();
            while (cursor.moveToNext()) {
                long playlistId = cursor.getLong(0);
                Playlist playlist = grouped.get(playlistId);
                if (playlist == null) {
                    playlist = new Playlist(cursor.getString(1));
                    grouped.put(playlistId, playlist);
                }
                if (!cursor.isNull(2)) {
                    playlist.uris.add(cursor.getString(2));
                }
            }
            playlists.addAll(grouped.values());
        } finally {
            closeQuietly(cursor);
        }
        return playlists;
    }

    void savePlaylists(List<Playlist> playlists) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            savePlaylists(db, playlists);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void saveCollections(Set<String> favorites, List<Playlist> playlists) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            saveFavorites(db, favorites);
            savePlaylists(db, playlists);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void createVersion2(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, "
                + "uri TEXT UNIQUE NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, "
                + "album TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL "
                + "DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, last_modified INTEGER "
                + "NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', "
                + "availability_reason TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE favorites (track_id TEXT PRIMARY KEY NOT NULL)");
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, position INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, "
                + "track_id TEXT NOT NULL, position INTEGER NOT NULL, "
                + "PRIMARY KEY (playlist_id, track_id))");
    }

    private static void migrateVersion1To2(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE tracks RENAME TO tracks_v1");
        db.execSQL("ALTER TABLE favorites RENAME TO favorites_v1");
        db.execSQL("ALTER TABLE playlist_tracks RENAME TO playlist_tracks_v1");
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
                values.put("title", tracks.getString(tracks.getColumnIndexOrThrow("title")));
                values.put("artist", tracks.getString(tracks.getColumnIndexOrThrow("artist")));
                values.put("album", tracks.getString(tracks.getColumnIndexOrThrow("album")));
                values.put("genre", tracks.getString(tracks.getColumnIndexOrThrow("genre")));
                values.put("duration_ms",
                        tracks.getInt(tracks.getColumnIndexOrThrow("duration_ms")));
                db.insertOrThrow("tracks", null, values);
            }
        } finally {
            tracks.close();
        }
        copyFavorites(db, ids);
        copyPlaylistTracks(db, ids);
        db.execSQL("DROP TABLE tracks_v1");
        db.execSQL("DROP TABLE favorites_v1");
        db.execSQL("DROP TABLE playlist_tracks_v1");
    }

    private static void copyFavorites(SQLiteDatabase db, Map<String, String> ids) {
        Cursor cursor = db.query("favorites_v1", new String[]{"uri"}, null, null, null,
                null, null);
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

    private static void saveTracks(SQLiteDatabase db, List<Track> tracks) {
        HashMap<String, Track> storedById = new HashMap<>();
        Cursor cursor = db.query("tracks", null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                Track stored = trackFromCursor(cursor);
                storedById.put(stored.trackId, stored);
            }
        } finally {
            cursor.close();
        }
        for (Track track : tracks) {
            Track stored = storedById.remove(track.trackId);
            if (stored == null) {
                db.insertWithOnConflict("tracks", null, trackValues(track),
                        SQLiteDatabase.CONFLICT_REPLACE);
            } else if (!sameStoredTrack(stored, track)) {
                db.update("tracks", trackValues(track), "track_id=?",
                        new String[]{track.trackId});
            }
        }
        for (String removedTrackId : storedById.keySet()) {
            db.delete("favorites", "track_id=?", new String[]{removedTrackId});
            db.delete("playlist_tracks", "track_id=?", new String[]{removedTrackId});
            db.delete("tracks", "track_id=?", new String[]{removedTrackId});
        }
    }

    private static boolean sameStoredTrack(Track left, Track right) {
        return left.durationMs == right.durationMs
                && left.fileSize == right.fileSize
                && left.lastModified == right.lastModified
                && equal(left.uri, right.uri)
                && equal(left.title, right.title)
                && equal(left.artist, right.artist)
                && equal(left.album, right.album)
                && equal(left.genre, right.genre)
                && equal(left.fingerprint, right.fingerprint);
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static ContentValues trackValues(Track track) {
        ContentValues values = new ContentValues();
        values.put("track_id", track.trackId);
        values.put("uri", track.uri);
        values.put("title", track.title);
        values.put("artist", track.artist);
        values.put("album", track.album);
        values.put("genre", track.genre);
        values.put("duration_ms", track.durationMs);
        values.put("file_size", track.fileSize);
        values.put("last_modified", track.lastModified);
        values.put("fingerprint", track.fingerprint);
        values.put("availability_reason", "");
        return values;
    }

    private static Track trackFromCursor(Cursor cursor) {
        return new Track(cursor.getString(cursor.getColumnIndexOrThrow("track_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("uri")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("artist")),
                cursor.getString(cursor.getColumnIndexOrThrow("album")),
                cursor.getString(cursor.getColumnIndexOrThrow("genre")),
                cursor.getInt(cursor.getColumnIndexOrThrow("duration_ms")),
                cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
                cursor.getLong(cursor.getColumnIndexOrThrow("last_modified")),
                cursor.getString(cursor.getColumnIndexOrThrow("fingerprint")));
    }

    private static Map<String, String> idsByUri(SQLiteDatabase db) {
        Map<String, String> ids = new HashMap<>();
        Cursor cursor = db.query("tracks", new String[]{"uri", "track_id"}, null, null,
                null, null, null);
        try {
            while (cursor.moveToNext()) {
                ids.put(cursor.getString(0), cursor.getString(1));
            }
        } finally {
            cursor.close();
        }
        return ids;
    }

    private static void saveFavorites(SQLiteDatabase db, Set<String> favorites) {
        Map<String, String> ids = idsByUri(db);
        db.delete("favorites", null, null);
        for (String uri : favorites) {
            String trackId = ids.get(uri);
            if (trackId != null) {
                ContentValues values = new ContentValues();
                values.put("track_id", trackId);
                db.insertWithOnConflict("favorites", null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

    private static void savePlaylists(SQLiteDatabase db, List<Playlist> playlists) {
        Map<String, String> ids = idsByUri(db);
        db.delete("playlist_tracks", null, null);
        db.delete("playlists", null, null);
        for (int index = 0; index < playlists.size(); index++) {
            Playlist playlist = playlists.get(index);
            ContentValues values = new ContentValues();
            values.put("name", PlaylistManager.cleanName(playlist.name));
            values.put("position", index);
            long playlistId = db.insert("playlists", null, values);
            for (int songIndex = 0; songIndex < playlist.uris.size(); songIndex++) {
                String trackId = ids.get(playlist.uris.get(songIndex));
                if (trackId == null) {
                    continue;
                }
                ContentValues song = new ContentValues();
                song.put("playlist_id", playlistId);
                song.put("track_id", trackId);
                song.put("position", songIndex);
                db.insertWithOnConflict("playlist_tracks", null, song,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

    private static long countRows(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private static void closeQuietly(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }
}
