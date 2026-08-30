package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SoundDatabaseMigrationInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @Test
    public void versionSixAddsSoundTablesWithoutChangingTracks() {
        SQLiteDatabase old = context.openOrCreateDatabase(LibraryDatabase.DB_NAME, 0, null);
        LibraryDatabaseSchema.createLatest(old);
        old.execSQL("DROP TRIGGER delete_track_audio_profile");
        old.execSQL("DROP TABLE sound_groups");
        old.execSQL("DROP TABLE audio_profiles");
        ContentValues track = LibraryDatabase.trackValues(new Track("migration-sound",
                "content://migration/sound", "Song", "Artist", "Album", "Genre",
                120000, 10L, 20L, "fingerprint"));
        old.insertOrThrow("tracks", null, track);
        old.setVersion(6);
        old.close();

        LibraryDatabase migrated = new LibraryDatabase(context);
        assertEquals(1, migrated.loadTracks().size());
        assertTrue(tableExists(migrated.getReadableDatabase(), "audio_profiles"));
        assertTrue(tableExists(migrated.getReadableDatabase(), "sound_groups"));
        migrated.close();
    }

    private static boolean tableExists(SQLiteDatabase database, String name) {
        Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master "
                + "WHERE type='table' AND name=?", new String[]{name});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }
}
