package com.dumuzeyn.mp3player;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

/** Collapses duplicate physical files while preserving user-owned library data. */
final class LibraryDuplicateCleaner {
    private LibraryDuplicateCleaner() {
    }

    static void clean(SQLiteDatabase db) {
        ArrayList<Track> keepers = new ArrayList<>();
        Cursor cursor = db.query("tracks", null, null, null, null, null,
                "CASE WHEN uri LIKE 'content://media/%' THEN 0 ELSE 1 END, date_added ASC");
        try {
            while (cursor.moveToNext()) {
                Track candidate = LibraryDatabase.trackFromCursor(cursor);
                int duplicateIndex = TrackDuplicatePolicy.duplicateIndex(keepers, candidate);
                if (duplicateIndex < 0) {
                    keepers.add(candidate);
                    continue;
                }
                Track keeper = keepers.get(duplicateIndex);
                Track merged = merge(keeper, candidate);
                mergeReferences(db, keeper, candidate);
                db.update("tracks", mergedValues(merged), "track_id=?",
                        new String[]{keeper.trackId});
                db.delete("tracks", "track_id=?", new String[]{candidate.trackId});
                keepers.set(duplicateIndex, merged);
            }
        } finally {
            cursor.close();
        }
    }

    private static void mergeReferences(SQLiteDatabase db, Track keeper, Track duplicate) {
        db.execSQL("INSERT OR IGNORE INTO favorites(track_id) "
                        + "SELECT ? WHERE EXISTS(SELECT 1 FROM favorites WHERE track_id=?)",
                new Object[]{keeper.trackId, duplicate.trackId});
        db.delete("favorites", "track_id=?", new String[]{duplicate.trackId});
        db.execSQL("INSERT OR IGNORE INTO playlist_tracks(playlist_id, track_id, position) "
                        + "SELECT playlist_id, ?, position FROM playlist_tracks WHERE track_id=?",
                new Object[]{keeper.trackId, duplicate.trackId});
        db.delete("playlist_tracks", "track_id=?", new String[]{duplicate.trackId});

        if (!isMediaStore(keeper.uri) && !hasSource(db, keeper.trackId)) {
            ContentValues sourceOwner = new ContentValues();
            sourceOwner.put("track_id", keeper.trackId);
            db.updateWithOnConflict("track_sources", sourceOwner, "track_id=?",
                    new String[]{duplicate.trackId}, SQLiteDatabase.CONFLICT_IGNORE);
        }
        db.delete("track_sources", "track_id=?", new String[]{duplicate.trackId});
    }

    private static boolean hasSource(SQLiteDatabase db, String trackId) {
        Cursor cursor = db.query("track_sources", new String[]{"track_id"}, "track_id=?",
                new String[]{trackId}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private static boolean isMediaStore(String uri) {
        return uri != null && uri.startsWith("content://media/");
    }

    private static Track merge(Track keeper, Track duplicate) {
        String fingerprint = keeper.fingerprint.isEmpty()
                ? duplicate.fingerprint : keeper.fingerprint;
        long fileSize = keeper.fileSize > 0L ? keeper.fileSize : duplicate.fileSize;
        long modified = Math.max(keeper.lastModified, duplicate.lastModified);
        return new Track(keeper.trackId, keeper.uri, keeper.title, keeper.artist, keeper.album,
                keeper.albumArtist, keeper.genre, keeper.year, keeper.trackNumber,
                keeper.discNumber, keeper.durationMs, fileSize, modified, fingerprint,
                sum(keeper.playCount, duplicate.playCount),
                sum(keeper.skipCount, duplicate.skipCount),
                earliest(keeper.dateAdded, duplicate.dateAdded),
                Math.max(keeper.lastPlayedAt, duplicate.lastPlayedAt),
                Math.max(keeper.lastCompletedAt, duplicate.lastCompletedAt));
    }

    private static ContentValues mergedValues(Track track) {
        ContentValues values = new ContentValues();
        values.put("file_size", track.fileSize);
        values.put("last_modified", track.lastModified);
        values.put("fingerprint", track.fingerprint);
        values.put("play_count", track.playCount);
        values.put("skip_count", track.skipCount);
        values.put("date_added", track.dateAdded);
        values.put("last_played_at", track.lastPlayedAt);
        values.put("last_completed_at", track.lastCompletedAt);
        return values;
    }

    private static int sum(int first, int second) {
        return (int) Math.min(Integer.MAX_VALUE, (long) first + second);
    }

    private static long earliest(long first, long second) {
        if (first <= 0L) return second;
        if (second <= 0L) return first;
        return Math.min(first, second);
    }
}
