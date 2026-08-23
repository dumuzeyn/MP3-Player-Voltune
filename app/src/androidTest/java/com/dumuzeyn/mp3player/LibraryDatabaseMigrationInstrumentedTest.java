package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
}
