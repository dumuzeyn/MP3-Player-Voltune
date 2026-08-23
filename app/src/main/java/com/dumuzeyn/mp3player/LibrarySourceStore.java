package com.dumuzeyn.mp3player;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.util.ArrayList;

/** SQLite access for SAF sources, track ownership, and exclusion tombstones. */
final class LibrarySourceStore implements AutoCloseable {
    private final LibraryDatabase database;

    LibrarySourceStore(Context context) {
        database = new LibraryDatabase(context);
    }

    LibrarySource remember(Uri treeUri, String displayName, boolean explicitImport) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            LibrarySource existing = find(db, treeUri);
            long revision = existing == null ? 1L
                    : existing.revision + (explicitImport ? 1L : 0L);
            ContentValues values = new ContentValues();
            values.put("source_id", LibrarySource.idFor(treeUri));
            values.put("tree_uri", treeUri.toString());
            values.put("display_name", cleanName(displayName));
            values.put("revision", revision);
            values.put("added_at", System.currentTimeMillis());
            db.insertWithOnConflict("library_sources", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            if (explicitImport) {
                db.delete("excluded_tracks", "source_id=?",
                        new String[]{LibrarySource.idFor(treeUri)});
            }
            db.setTransactionSuccessful();
            return new LibrarySource(LibrarySource.idFor(treeUri), treeUri.toString(),
                    cleanName(displayName), revision);
        } finally {
            db.endTransaction();
        }
    }

    ArrayList<LibrarySource> list() {
        ArrayList<LibrarySource> result = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().query("library_sources", null,
                null, null, null, null, "display_name COLLATE NOCASE ASC");
        try {
            while (cursor.moveToNext()) {
                result.add(source(cursor));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    LibrarySource find(Uri treeUri) {
        return find(database.getReadableDatabase(), treeUri);
    }

    LibrarySource findById(String sourceId) {
        Cursor cursor = database.getReadableDatabase().query("library_sources", null,
                "source_id=?", new String[]{sourceId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? source(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    TrackOrigin originForTrack(String trackId) {
        Cursor cursor = database.getReadableDatabase().query("track_sources", null,
                "track_id=?", new String[]{trackId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? new TrackOrigin(cursor.getString(0),
                    cursor.getString(1), cursor.getString(2), cursor.getString(3)) : null;
        } finally {
            cursor.close();
        }
    }

    ArrayList<ExcludedTrack> exclusions(String sourceId) {
        ArrayList<ExcludedTrack> result = new ArrayList<>();
        String selection = sourceId == null ? null : "source_id=? OR source_id=''";
        String[] args = sourceId == null ? null : new String[]{sourceId};
        Cursor cursor = database.getReadableDatabase().query("excluded_tracks", null,
                selection, args, null, null, null);
        try {
            while (cursor.moveToNext()) {
                result.add(new ExcludedTrack(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3), cursor.getLong(4),
                        cursor.getLong(5), cursor.getString(6)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    void clearMatchingExclusions(Track track) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("excluded_tracks", "identity_key=? OR uri=?",
                    new String[]{TrackOrigin.uriIdentity(track.uri), track.uri});
            if (!track.fingerprint.isEmpty() && track.fileSize > 0L) {
                db.delete("excluded_tracks", "fingerprint=? AND file_size=?",
                        new String[]{track.fingerprint, String.valueOf(track.fileSize)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void close() {
        database.close();
    }

    private static LibrarySource find(SQLiteDatabase db, Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        Cursor cursor = db.query("library_sources", null, "tree_uri=?",
                new String[]{treeUri.toString()}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? source(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private static LibrarySource source(Cursor cursor) {
        return new LibrarySource(cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("tree_uri")),
                cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                cursor.getLong(cursor.getColumnIndexOrThrow("revision")));
    }

    private static String cleanName(String name) {
        String value = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty()) {
            return "Music folder";
        }
        return value.length() > 160 ? value.substring(0, 160).trim() : value;
    }
}
