package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LibraryImportStoreInstrumentedTest {
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
    public void exclusionCreatedDuringScanWinsAtCommit() {
        Track original = track("original", "A");
        addOwnedTrack(original, "doc-A");
        LibraryImportStore imports = new LibraryImportStore(context);
        SourceScanSession runningScan = imports.session(source);
        LibraryMutationStore mutations = new LibraryMutationStore(context);
        mutations.removeTrack(original);
        mutations.close();

        Track rediscovered = track("rediscovered", "A");
        assertTrue(imports.commitSource(runningScan, Collections.singletonList(
                new DiscoveredTrack(rediscovered, source, "doc-A"))).isEmpty());
        imports.close();
        LibraryDatabase database = new LibraryDatabase(context);
        assertTrue(database.loadTracks().isEmpty());
        database.close();
    }

    @Test
    public void removedSourceInvalidatesRunningScan() {
        LibraryImportStore imports = new LibraryImportStore(context);
        SourceScanSession runningScan = imports.session(source);
        LibraryMutationStore mutations = new LibraryMutationStore(context);
        mutations.removeSource(source.sourceId);
        mutations.close();

        assertTrue(imports.commitSource(runningScan, Collections.singletonList(
                new DiscoveredTrack(track("rediscovered", "A"), source, "doc-A"))).isEmpty());
        imports.close();
    }

    @Test
    public void explicitFileImportClearsExclusionWithoutCreatingDuplicate() {
        Track original = track("original", "A");
        LibraryDatabase database = new LibraryDatabase(context);
        database.upsertTrack(original);
        database.close();
        LibraryMutationStore mutations = new LibraryMutationStore(context);
        mutations.removeTrack(original);
        mutations.close();
        Track selected = track("selected", "A");
        LibraryImportStore imports = new LibraryImportStore(context);

        assertEquals(1, imports.commitStandalone(Arrays.asList(selected, selected), true).size());
        imports.close();
        database = new LibraryDatabase(context);
        assertEquals(1, database.loadTracks().size());
        database.close();
        LibrarySourceStore sources = new LibrarySourceStore(context);
        assertTrue(sources.exclusions(null).isEmpty());
        sources.close();
    }

    @Test
    public void automaticScanSkipsSameFileFromAnotherProvider() {
        Track document = track("document", "A");
        LibraryDatabase database = new LibraryDatabase(context);
        database.upsertTrack(document);
        database.close();
        Track mediaStore = new Track("track-media", "content://media/audio/77",
                "Song", "Artist", "Album", "Rock", 120000, 2048L, 42L,
                "fingerprint-A");
        LibraryImportStore imports = new LibraryImportStore(context);

        assertTrue(imports.commitStandalone(
                Collections.singletonList(mediaStore), false).isEmpty());
        imports.close();
        database = new LibraryDatabase(context);
        assertEquals(1, database.loadTracks().size());
        database.close();
    }

    private void addOwnedTrack(Track track, String documentId) {
        LibraryDatabase database = new LibraryDatabase(context);
        database.upsertTrack(track);
        SQLiteDatabase db = database.getWritableDatabase();
        ContentValues origin = new ContentValues();
        origin.put("track_id", track.trackId);
        origin.put("source_id", source.sourceId);
        origin.put("document_id", documentId);
        origin.put("identity_key", TrackOrigin.identity(source.sourceId, documentId));
        db.insertOrThrow("track_sources", null, origin);
        database.close();
    }

    private static Track track(String id, String document) {
        return new Track("track-" + id, "content://provider/document/" + document,
                "Song", "Artist", "Album", "Rock", 120000, 2048L, 42L,
                "fingerprint-" + document);
    }
}
