package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LibraryDatabaseMigrationInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        context.getSharedPreferences("mp3_player_store", Context.MODE_PRIVATE).edit()
                .putBoolean("sqlite_migrated", true)
                .commit();
    }

    @After
    public void tearDown() {
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @Test
    public void versionOneMigrationPreservesTracksFavoritesAndPlaylists() {
        String uri = "content://migration/song-0.mp3";
        String lastUri = "content://migration/song-164.mp3";
        SQLiteDatabase old = context.openOrCreateDatabase(LibraryDatabase.DB_NAME, 0, null);
        old.execSQL("CREATE TABLE tracks (uri TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, "
                + "artist TEXT NOT NULL, album TEXT NOT NULL, genre TEXT NOT NULL, "
                + "duration_ms INTEGER NOT NULL DEFAULT 0)");
        old.execSQL("CREATE TABLE favorites (uri TEXT PRIMARY KEY NOT NULL)");
        old.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, position INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, "
                + "uri TEXT NOT NULL, position INTEGER NOT NULL, "
                + "PRIMARY KEY (playlist_id, uri))");
        for (int index = 0; index < 165; index++) {
            ContentValues track = new ContentValues();
            track.put("uri", "content://migration/song-" + index + ".mp3");
            track.put("title", "Migration song " + index);
            track.put("artist", "Artist");
            track.put("album", "Album");
            track.put("genre", "Genre");
            track.put("duration_ms", 123000 + index);
            old.insertOrThrow("tracks", null, track);
        }
        ContentValues favorite = new ContentValues();
        favorite.put("uri", uri);
        old.insertOrThrow("favorites", null, favorite);
        ContentValues playlist = new ContentValues();
        playlist.put("name", "Preserved");
        playlist.put("position", 0);
        long playlistId = old.insertOrThrow("playlists", null, playlist);
        ContentValues member = new ContentValues();
        member.put("playlist_id", playlistId);
        member.put("uri", uri);
        member.put("position", 0);
        old.insertOrThrow("playlist_tracks", null, member);
        ContentValues lastMember = new ContentValues();
        lastMember.put("playlist_id", playlistId);
        lastMember.put("uri", lastUri);
        lastMember.put("position", 1);
        old.insertOrThrow("playlist_tracks", null, lastMember);
        old.setVersion(1);
        old.close();

        LibraryDatabase migrated = new LibraryDatabase(context);
        ArrayList<Track> tracks = migrated.loadTracks();
        HashSet<String> favorites = migrated.loadFavorites();
        ArrayList<Playlist> playlists = migrated.loadPlaylists();
        migrated.close();

        assertEquals(165, tracks.size());
        assertEquals(TrackIdentity.fromLegacyUri(uri), tracks.get(0).trackId);
        assertFalse(tracks.get(0).trackId.equals(uri));
        assertEquals(123000, tracks.get(0).durationMs);
        assertEquals(1, favorites.size());
        assertEquals(1, playlists.size());
        assertEquals(2, playlists.get(0).uris.size());
        assertEquals(uri, playlists.get(0).uris.get(0));
        assertEquals(lastUri, playlists.get(0).uris.get(1));
    }

    @Test
    public void versionTwoMigrationAddsStatisticsWithoutLosingLibraryData() {
        SQLiteDatabase old = context.openOrCreateDatabase(LibraryDatabase.DB_NAME, 0, null);
        old.execSQL("CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, "
                + "uri TEXT UNIQUE NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, "
                + "album TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL "
                + "DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, last_modified INTEGER "
                + "NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', "
                + "availability_reason TEXT NOT NULL DEFAULT '')");
        old.execSQL("CREATE TABLE favorites (track_id TEXT PRIMARY KEY NOT NULL)");
        old.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, position INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, "
                + "track_id TEXT NOT NULL, position INTEGER NOT NULL, "
                + "PRIMARY KEY (playlist_id, track_id))");
        ContentValues values = new ContentValues();
        values.put("track_id", "stable-v2");
        values.put("uri", "content://migration/v2");
        values.put("title", "Song");
        values.put("artist", "Artist");
        values.put("album", "Album");
        values.put("genre", "Genre");
        values.put("last_modified", 1234L);
        old.insertOrThrow("tracks", null, values);
        old.setVersion(2);
        old.close();
        LibraryDatabase migrated = new LibraryDatabase(context);
        ArrayList<Track> tracks = migrated.loadTracks();
        migrated.close();

        assertEquals(1, tracks.size());
        assertEquals("Song", tracks.get(0).title);
        assertEquals(1234L, tracks.get(0).dateAdded);
        assertEquals("Artist", tracks.get(0).albumArtist);
    }

    @Test
    public void versionThreeMigrationSchedulesOneMetadataRepair() {
        SQLiteDatabase old = context.openOrCreateDatabase(LibraryDatabase.DB_NAME, 0, null);
        old.execSQL("CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, "
                + "uri TEXT UNIQUE NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, "
                + "album TEXT NOT NULL, album_artist TEXT NOT NULL DEFAULT '', "
                + "genre TEXT NOT NULL, year INTEGER NOT NULL DEFAULT 0, "
                + "track_number INTEGER NOT NULL DEFAULT 0, disc_number INTEGER NOT NULL DEFAULT 0, "
                + "duration_ms INTEGER NOT NULL DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, "
                + "last_modified INTEGER NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', "
                + "availability_reason TEXT NOT NULL DEFAULT '', play_count INTEGER NOT NULL DEFAULT 0, "
                + "skip_count INTEGER NOT NULL DEFAULT 0, date_added INTEGER NOT NULL DEFAULT 0, "
                + "last_played_at INTEGER NOT NULL DEFAULT 0, last_completed_at INTEGER NOT NULL DEFAULT 0)");
        old.execSQL("CREATE TABLE favorites (track_id TEXT PRIMARY KEY NOT NULL)");
        old.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, position INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, "
                + "track_id TEXT NOT NULL, position INTEGER NOT NULL, "
                + "PRIMARY KEY (playlist_id, track_id))");
        ContentValues values = new ContentValues();
        values.put("track_id", "stable-v3");
        values.put("uri", "content://migration/v3");
        values.put("title", "Song");
        values.put("artist", "Artist");
        values.put("album", "Album");
        values.put("album_artist", "Artist");
        values.put("genre", "Unknown genre");
        old.insertOrThrow("tracks", null, values);
        old.setVersion(3);
        old.close();

        LibraryDatabase migrated = new LibraryDatabase(context);
        ArrayList<Track> candidates = migrated.loadTracksNeedingMetadataRefresh(1);
        assertEquals(1, candidates.size());
        migrated.updateTrackMetadata(candidates.get(0).withMetadata(
                "Song", "Artist", "Album", "Artist", "Rock", 0, 0, 0));
        assertEquals(0, migrated.loadTracksNeedingMetadataRefresh(1).size());
        migrated.close();
    }

    @Test
    public void versionFourMigrationKeepsLibraryAndAddsSourceTables() {
        SQLiteDatabase old = context.openOrCreateDatabase(LibraryDatabase.DB_NAME, 0, null);
        LibraryDatabaseSchema.createLatest(old);
        old.execSQL("DROP TABLE excluded_tracks");
        old.execSQL("DROP TABLE track_sources");
        old.execSQL("DROP TABLE library_sources");
        ContentValues track = new ContentValues();
        track.put("track_id", "stable-v4");
        track.put("uri", "content://migration/v4");
        track.put("title", "Preserved song");
        track.put("artist", "Artist");
        track.put("album", "Album");
        track.put("album_artist", "Artist");
        track.put("genre", "Rock");
        old.insertOrThrow("tracks", null, track);
        ContentValues favorite = new ContentValues();
        favorite.put("track_id", "stable-v4");
        old.insertOrThrow("favorites", null, favorite);
        old.setVersion(4);
        old.close();

        LibraryDatabase migrated = new LibraryDatabase(context);
        assertEquals(1, migrated.loadTracks().size());
        assertEquals(1, migrated.loadFavorites().size());
        assertTrue(tableExists(migrated.getReadableDatabase(), "library_sources"));
        assertTrue(tableExists(migrated.getReadableDatabase(), "track_sources"));
        assertTrue(tableExists(migrated.getReadableDatabase(), "excluded_tracks"));
        migrated.close();
    }

    @Test
    public void versionFiveMigrationCollapsesProviderDuplicatesAndKeepsCollections() {
        SQLiteDatabase old = context.openOrCreateDatabase(LibraryDatabase.DB_NAME, 0, null);
        LibraryDatabaseSchema.createLatest(old);
        Track mediaStore = new Track("media", "content://media/audio/77", "Song",
                "Artist", "Album", "Artist", "Rock", 2024, 1, 1, 120000,
                4096L, 42L, "", 2, 1, 200L, 300L, 0L);
        Track document = new Track("document", "content://documents/music/song", "Song",
                "Artist", "Album", "Artist", "Rock", 2024, 1, 1, 120000,
                4096L, 42L, "same-hash", 3, 2, 100L, 400L, 500L);
        old.insertOrThrow("tracks", null, LibraryDatabase.trackValues(mediaStore));
        old.insertOrThrow("tracks", null, LibraryDatabase.trackValues(document));
        ContentValues favorite = new ContentValues();
        favorite.put("track_id", document.trackId);
        old.insertOrThrow("favorites", null, favorite);
        ContentValues playlist = new ContentValues();
        playlist.put("name", "Migration duplicates");
        playlist.put("position", 0);
        long playlistId = old.insertOrThrow("playlists", null, playlist);
        ContentValues member = new ContentValues();
        member.put("playlist_id", playlistId);
        member.put("track_id", document.trackId);
        member.put("position", 0);
        old.insertOrThrow("playlist_tracks", null, member);
        ContentValues source = new ContentValues();
        source.put("source_id", "source");
        source.put("tree_uri", "content://documents/tree/music");
        source.put("display_name", "Music");
        old.insertOrThrow("library_sources", null, source);
        ContentValues ownership = new ContentValues();
        ownership.put("track_id", document.trackId);
        ownership.put("source_id", "source");
        ownership.put("document_id", "song");
        ownership.put("identity_key", "document:source:song");
        old.insertOrThrow("track_sources", null, ownership);
        old.setVersion(5);
        old.close();

        LibraryDatabase migrated = new LibraryDatabase(context);
        ArrayList<Track> tracks = migrated.loadTracks();

        assertEquals(1, tracks.size());
        assertEquals("media", tracks.get(0).trackId);
        assertEquals(5, tracks.get(0).playCount);
        assertEquals(3, tracks.get(0).skipCount);
        assertEquals(100L, tracks.get(0).dateAdded);
        assertEquals(1, migrated.loadFavorites().size());
        assertEquals(1, migrated.loadPlaylists().get(0).uris.size());
        assertEquals(0, countRows(migrated.getReadableDatabase(), "track_sources"));
        migrated.close();
    }

    private static int countRows(SQLiteDatabase database, String table) {
        android.database.Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private static boolean tableExists(SQLiteDatabase database, String name) {
        android.database.Cursor cursor = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{name});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }
}
