package com.dumuzeyn.mp3player

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlin.math.max
import kotlin.math.min

/** Collapses duplicate physical files while preserving user-owned library data. */
internal object LibraryDuplicateCleaner {
    @JvmStatic
    fun clean(db: SQLiteDatabase) {
        val keepers = ArrayList<Track>()
        db.query(
            "tracks",
            null,
            null,
            null,
            null,
            null,
            "CASE WHEN uri LIKE 'content://media/%' THEN 0 ELSE 1 END, date_added ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val candidate = LibraryDatabase.trackFromCursor(cursor)
                val duplicateIndex = TrackDuplicatePolicy.duplicateIndex(keepers, candidate)
                if (duplicateIndex < 0) {
                    keepers.add(candidate)
                    continue
                }
                val keeper = keepers[duplicateIndex]
                val merged = merge(keeper, candidate)
                mergeReferences(db, keeper, candidate)
                db.update(
                    "tracks",
                    mergedValues(merged),
                    "track_id=?",
                    arrayOf(keeper.trackId),
                )
                db.delete("tracks", "track_id=?", arrayOf(candidate.trackId))
                keepers[duplicateIndex] = merged
            }
        }
    }

    private fun mergeReferences(db: SQLiteDatabase, keeper: Track, duplicate: Track) {
        db.execSQL(
            "INSERT OR IGNORE INTO favorites(track_id) " +
                "SELECT ? WHERE EXISTS(SELECT 1 FROM favorites WHERE track_id=?)",
            arrayOf(keeper.trackId, duplicate.trackId),
        )
        db.delete("favorites", "track_id=?", arrayOf(duplicate.trackId))
        db.execSQL(
            "INSERT OR IGNORE INTO playlist_tracks(playlist_id, track_id, position) " +
                "SELECT playlist_id, ?, position FROM playlist_tracks WHERE track_id=?",
            arrayOf(keeper.trackId, duplicate.trackId),
        )
        db.delete("playlist_tracks", "track_id=?", arrayOf(duplicate.trackId))

        if (!isMediaStore(keeper.uri) && !hasSource(db, keeper.trackId)) {
            val sourceOwner = ContentValues().apply { put("track_id", keeper.trackId) }
            db.updateWithOnConflict(
                "track_sources",
                sourceOwner,
                "track_id=?",
                arrayOf(duplicate.trackId),
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
        db.delete("track_sources", "track_id=?", arrayOf(duplicate.trackId))
    }

    private fun hasSource(db: SQLiteDatabase, trackId: String): Boolean =
        db.query(
            "track_sources",
            arrayOf("track_id"),
            "track_id=?",
            arrayOf(trackId),
            null,
            null,
            null,
            "1",
        ).use { cursor -> cursor.moveToFirst() }

    private fun isMediaStore(uri: String?): Boolean = uri?.startsWith("content://media/") == true

    private fun merge(keeper: Track, duplicate: Track): Track = Track(
        keeper.trackId,
        keeper.uri,
        keeper.title,
        keeper.artist,
        keeper.album,
        keeper.albumArtist,
        keeper.genre,
        keeper.year,
        keeper.trackNumber,
        keeper.discNumber,
        keeper.durationMs,
        if (keeper.fileSize > 0L) keeper.fileSize else duplicate.fileSize,
        max(keeper.lastModified, duplicate.lastModified),
        keeper.fingerprint.ifEmpty { duplicate.fingerprint },
        sum(keeper.playCount, duplicate.playCount),
        sum(keeper.skipCount, duplicate.skipCount),
        earliest(keeper.dateAdded, duplicate.dateAdded),
        max(keeper.lastPlayedAt, duplicate.lastPlayedAt),
        max(keeper.lastCompletedAt, duplicate.lastCompletedAt),
    )

    private fun mergedValues(track: Track): ContentValues = ContentValues().apply {
        put("file_size", track.fileSize)
        put("last_modified", track.lastModified)
        put("fingerprint", track.fingerprint)
        put("play_count", track.playCount)
        put("skip_count", track.skipCount)
        put("date_added", track.dateAdded)
        put("last_played_at", track.lastPlayedAt)
        put("last_completed_at", track.lastCompletedAt)
    }

    private fun sum(first: Int, second: Int): Int =
        min(Int.MAX_VALUE.toLong(), first.toLong() + second).toInt()

    private fun earliest(first: Long, second: Long): Long = when {
        first <= 0L -> second
        second <= 0L -> first
        else -> min(first, second)
    }
}
