package com.dumuzeyn.mp3player;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Atomic persisted mutations for user-directed library removal. */
final class LibraryMutationStore implements AutoCloseable {
    private final LibraryDatabase database;

    LibraryMutationStore(Context context) {
        database = new LibraryDatabase(context);
    }

    RemovedLibraryItems removeTrack(Track track) {
        return removeTrack(track, true);
    }

    RemovedLibraryItems removeDeletedFile(Track track) {
        return removeTrack(track, false);
    }

    private RemovedLibraryItems removeTrack(Track track, boolean createExclusion) {
        RemovedLibraryItems removed = new RemovedLibraryItems();
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            if (createExclusion) {
                TrackOrigin origin = origin(db, track.trackId);
                insertExclusion(db, ExcludedTrack.from(track, origin));
            }
            deleteCollectionsAndTrack(db, track.trackId);
            removed.add(track.trackId, track.uri);
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    RemovedLibraryItems removeSource(String sourceId) {
        RemovedLibraryItems removed = new RemovedLibraryItems();
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            LibrarySource source = source(db, sourceId);
            if (source == null) {
                db.setTransactionSuccessful();
                return removed;
            }
            removed.sources.add(source);
            collectOwnedTracks(db, sourceId, removed);
            String owned = "SELECT track_id FROM track_sources WHERE source_id=?";
            db.delete("favorites", "track_id IN (" + owned + ")", new String[]{sourceId});
            db.delete("playlist_tracks", "track_id IN (" + owned + ")",
                    new String[]{sourceId});
            db.delete("tracks", "track_id IN (" + owned + ")", new String[]{sourceId});
            db.delete("track_sources", "source_id=?", new String[]{sourceId});
            db.delete("excluded_tracks", "source_id=?", new String[]{sourceId});
            db.delete("library_sources", "source_id=?", new String[]{sourceId});
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    RemovedLibraryItems clearLibrary() {
        RemovedLibraryItems removed = new RemovedLibraryItems();
        removed.clearQueue = true;
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor tracks = db.query("tracks", new String[]{"track_id", "uri"},
                    null, null, null, null, null);
            try {
                while (tracks.moveToNext()) {
                    removed.add(tracks.getString(0), tracks.getString(1));
                }
            } finally {
                tracks.close();
            }
            removed.sources.addAll(sources(db));
            db.delete("favorites", null, null);
            db.delete("playlist_tracks", null, null);
            db.delete("track_sources", null, null);
            db.delete("tracks", null, null);
            db.delete("excluded_tracks", null, null);
            db.delete("library_sources", null, null);
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void close() {
        database.close();
    }

    private static void deleteCollectionsAndTrack(SQLiteDatabase db, String trackId) {
        String[] args = {trackId};
        db.delete("favorites", "track_id=?", args);
        db.delete("playlist_tracks", "track_id=?", args);
        db.delete("track_sources", "track_id=?", args);
        db.delete("tracks", "track_id=?", args);
    }

    private static void insertExclusion(SQLiteDatabase db, ExcludedTrack exclusion) {
        ContentValues values = new ContentValues();
        values.put("identity_key", exclusion.identityKey);
        values.put("source_id", exclusion.sourceId);
        values.put("document_id", exclusion.documentId);
        values.put("uri", exclusion.uri);
        values.put("file_size", exclusion.fileSize);
        values.put("last_modified", exclusion.lastModified);
        values.put("fingerprint", exclusion.fingerprint);
        values.put("removed_at", System.currentTimeMillis());
        db.insertWithOnConflict("excluded_tracks", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static TrackOrigin origin(SQLiteDatabase db, String trackId) {
        Cursor cursor = db.query("track_sources", null, "track_id=?",
                new String[]{trackId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? new TrackOrigin(cursor.getString(0),
                    cursor.getString(1), cursor.getString(2), cursor.getString(3)) : null;
        } finally {
            cursor.close();
        }
    }

    private static LibrarySource source(SQLiteDatabase db, String sourceId) {
        Cursor cursor = db.query("library_sources", null, "source_id=?",
                new String[]{sourceId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? source(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private static java.util.ArrayList<LibrarySource> sources(SQLiteDatabase db) {
        java.util.ArrayList<LibrarySource> result = new java.util.ArrayList<>();
        Cursor cursor = db.query("library_sources", null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                result.add(source(cursor));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    private static LibrarySource source(Cursor cursor) {
        return new LibrarySource(cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("tree_uri")),
                cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                cursor.getLong(cursor.getColumnIndexOrThrow("revision")));
    }

    private static void collectOwnedTracks(SQLiteDatabase db, String sourceId,
            RemovedLibraryItems removed) {
        Cursor cursor = db.rawQuery("SELECT tracks.track_id, tracks.uri FROM tracks "
                + "JOIN track_sources ON track_sources.track_id=tracks.track_id "
                + "WHERE track_sources.source_id=?", new String[]{sourceId});
        try {
            while (cursor.moveToNext()) {
                removed.add(cursor.getString(0), cursor.getString(1));
            }
        } finally {
            cursor.close();
        }
    }
}
