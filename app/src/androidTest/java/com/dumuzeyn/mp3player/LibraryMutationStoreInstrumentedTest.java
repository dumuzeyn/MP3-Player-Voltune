package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LibraryMutationStoreInstrumentedTest {
    private Context context;
    private LibrarySource source;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        LibrarySourceStore sources = new LibrarySourceStore(context);
        source = sources.remember(Uri.parse("content://provider/tree/music"), "Music", false);
        sources.close();
    }

    @After
    public void tearDown() {
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @Test
    public void removingTrackPersistsExclusionAcrossDatabaseRestart() {
        Track track = addTrack("A");
        LibraryMutationStore mutations = new LibraryMutationStore(context);
        mutations.removeTrack(track);
        mutations.close();

        LibraryDatabase reopened = new LibraryDatabase(context);
        assertTrue(reopened.loadTracks().isEmpty());
        reopened.close();
        LibrarySourceStore sources = new LibrarySourceStore(context);
        ExcludedTrackIndex exclusions = new ExcludedTrackIndex(
                sources.exclusions(source.sourceId));
        sources.close();
        assertTrue(exclusions.contains(TrackOrigin.identity(source.sourceId, "doc-A"), track));
    }

    @Test
    public void removingSourceAtomicallyClearsOwnedTracksAndCollections() {
        ArrayList<Track> tracks = new ArrayList<>();
        for (String id : Arrays.asList("A", "B", "C", "D")) {
            tracks.add(addTrack(id));
        }
        LibraryDatabase database = new LibraryDatabase(context);
        HashSet<String> favorites = new HashSet<>();
        favorites.add(tracks.get(0).uri);
        Playlist playlist = new Playlist("Owned");
        for (Track track : tracks) {
            playlist.uris.add(track.uri);
        }
        database.saveCollections(favorites, Arrays.asList(playlist));
        database.close();

        LibraryMutationStore mutations = new LibraryMutationStore(context);
        RemovedLibraryItems removed = mutations.removeSource(source.sourceId);
        mutations.close();

        LibraryDatabase reopened = new LibraryDatabase(context);
        assertEquals(4, removed.trackIds.size());
        assertTrue(reopened.loadTracks().isEmpty());
        assertTrue(reopened.loadFavorites().isEmpty());
        assertTrue(reopened.loadPlaylists().get(0).uris.isEmpty());
        reopened.close();
        LibrarySourceStore sources = new LibrarySourceStore(context);
        assertTrue(sources.list().isEmpty());
        sources.close();
    }

    @Test
    public void clearLibraryAlsoClearsSourcesAndExclusions() {
        Track track = addTrack("A");
        LibraryMutationStore mutations = new LibraryMutationStore(context);
        mutations.removeTrack(track);
        addTrack("B");
        RemovedLibraryItems removed = mutations.clearLibrary();
        mutations.close();

        LibraryDatabase database = new LibraryDatabase(context);
        assertTrue(database.loadTracks().isEmpty());
        database.close();
        LibrarySourceStore sources = new LibrarySourceStore(context);
        assertTrue(sources.list().isEmpty());
        assertTrue(sources.exclusions(null).isEmpty());
        sources.close();
        assertTrue(removed.clearQueue);
        assertFalse(removed.sources.isEmpty());
    }

    private Track addTrack(String id) {
        Track track = new Track("track-" + id, "content://provider/document/" + id,
                "Song " + id, "Artist", "Album", "Rock", 120000,
                1000L + id.charAt(0), 42L, "fingerprint-" + id);
        LibraryDatabase database = new LibraryDatabase(context);
        database.upsertTrack(track);
        SQLiteDatabase db = database.getWritableDatabase();
        ContentValues origin = new ContentValues();
        origin.put("track_id", track.trackId);
        origin.put("source_id", source.sourceId);
        origin.put("document_id", "doc-" + id);
        origin.put("identity_key", TrackOrigin.identity(source.sourceId, "doc-" + id));
        db.insertOrThrow("track_sources", null, origin);
        database.close();
        return track;
    }
}
