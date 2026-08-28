package com.dumuzeyn.mp3player;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/** Transactional import boundary that rechecks sources and exclusions at commit time. */
final class LibraryImportStore implements AutoCloseable {
    private final LibraryDatabase database;

    LibraryImportStore(Context context) {
        database = new LibraryDatabase(context);
    }

    SourceScanSession session(LibrarySource source) {
        return new SourceScanSession(source,
                new ExcludedTrackIndex(loadExclusions(database.getReadableDatabase(),
                        source.sourceId)));
    }

    ArrayList<Track> commitSource(SourceScanSession session,
            List<DiscoveredTrack> discovered) {
        ArrayList<Track> accepted = new ArrayList<>();
        ArrayList<Track> existing = database.loadTracks();
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            if (!sourceRevisionMatches(db, session.source)) {
                return accepted;
            }
            ExcludedTrackIndex exclusions = new ExcludedTrackIndex(
                    loadExclusions(db, session.source.sourceId));
            HashSet<String> acceptedUris = new HashSet<>();
            for (DiscoveredTrack item : discovered) {
                if (!acceptedUris.add(item.track.uri)
                        || exclusions.contains(item.identityKey, item.track)) {
                    continue;
                }
                int sameLocation = TrackDuplicatePolicy.sameLocationIndex(existing, item.track);
                Track acceptedTrack = item.track;
                if (sameLocation >= 0) {
                    acceptedTrack = TrackDuplicatePolicy.withStableIdentity(
                            existing.get(sameLocation), item.track);
                    existing.set(sameLocation, acceptedTrack);
                } else if (TrackDuplicatePolicy.duplicateIndex(existing, item.track) >= 0) {
                    continue;
                } else {
                    existing.add(item.track);
                }
                upsert(db, acceptedTrack);
                ContentValues origin = new ContentValues();
                origin.put("track_id", acceptedTrack.trackId);
                origin.put("source_id", session.source.sourceId);
                origin.put("document_id", item.documentId);
                origin.put("identity_key", item.identityKey);
                db.insertWithOnConflict("track_sources", null, origin,
                        SQLiteDatabase.CONFLICT_REPLACE);
                accepted.add(acceptedTrack);
            }
            db.setTransactionSuccessful();
            return accepted;
        } finally {
            db.endTransaction();
        }
    }

    ArrayList<Track> commitStandalone(List<Track> discovered, boolean explicitImport) {
        ArrayList<Track> accepted = new ArrayList<>();
        ArrayList<Track> existing = database.loadTracks();
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            ExcludedTrackIndex exclusions = new ExcludedTrackIndex(loadExclusions(db, null));
            HashSet<String> acceptedUris = new HashSet<>();
            for (Track track : discovered) {
                if (!acceptedUris.add(track.uri)) {
                    continue;
                }
                String identity = TrackOrigin.uriIdentity(track.uri);
                if (explicitImport) {
                    clearMatching(db, track);
                } else if (exclusions.contains(identity, track)) {
                    continue;
                }
                int sameLocation = TrackDuplicatePolicy.sameLocationIndex(existing, track);
                Track acceptedTrack = track;
                if (sameLocation >= 0) {
                    acceptedTrack = TrackDuplicatePolicy.withStableIdentity(
                            existing.get(sameLocation), track);
                    existing.set(sameLocation, acceptedTrack);
                } else if (TrackDuplicatePolicy.duplicateIndex(existing, track) >= 0) {
                    continue;
                } else {
                    existing.add(track);
                }
                upsert(db, acceptedTrack);
                accepted.add(acceptedTrack);
            }
            db.setTransactionSuccessful();
            return accepted;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void close() {
        database.close();
    }

    private static void upsert(SQLiteDatabase db, Track track) {
        ContentValues values = LibraryDatabase.trackValues(track);
        values.put("metadata_revision", LibraryMaintenanceController.METADATA_REVISION);
        db.insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static boolean sourceRevisionMatches(SQLiteDatabase db, LibrarySource source) {
        Cursor cursor = db.query("library_sources", new String[]{"revision"},
                "source_id=?", new String[]{source.sourceId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() && cursor.getLong(0) == source.revision;
        } finally {
            cursor.close();
        }
    }

    private static ArrayList<ExcludedTrack> loadExclusions(SQLiteDatabase db, String sourceId) {
        ArrayList<ExcludedTrack> result = new ArrayList<>();
        String selection = sourceId == null ? null : "source_id=? OR source_id=''";
        String[] args = sourceId == null ? null : new String[]{sourceId};
        Cursor cursor = db.query("excluded_tracks", null, selection, args,
                null, null, null);
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

    private static void clearMatching(SQLiteDatabase db, Track track) {
        db.delete("excluded_tracks", "identity_key=? OR uri=?",
                new String[]{TrackOrigin.uriIdentity(track.uri), track.uri});
        if (!track.fingerprint.isEmpty() && track.fileSize > 0L) {
            db.delete("excluded_tracks", "fingerprint=? AND file_size=?",
                    new String[]{track.fingerprint, String.valueOf(track.fileSize)});
        }
    }
}
