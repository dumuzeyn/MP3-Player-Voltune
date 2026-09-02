package com.dumuzeyn.mp3player

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** Atomic persisted mutations for user-directed library removal. */
class LibraryMutationStore(context: Context) : AutoCloseable {
    private val database = LibraryDatabase(context)

    fun removeTrack(track: Track): RemovedLibraryItems = removeTrack(track, true)

    fun removeDeletedFile(track: Track): RemovedLibraryItems = removeTrack(track, false)

    fun removeSource(sourceId: String): RemovedLibraryItems {
        val removed = RemovedLibraryItems()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val source = source(db, sourceId)
            if (source == null) {
                db.setTransactionSuccessful()
                return removed
            }
            removed.sources += source
            collectOwnedTracks(db, sourceId, removed)
            val owned = "SELECT track_id FROM track_sources WHERE source_id=?"
            db.delete("favorites", "track_id IN ($owned)", arrayOf(sourceId))
            db.delete("playlist_tracks", "track_id IN ($owned)", arrayOf(sourceId))
            db.delete("tracks", "track_id IN ($owned)", arrayOf(sourceId))
            db.delete("track_sources", "source_id=?", arrayOf(sourceId))
            db.delete("excluded_tracks", "source_id=?", arrayOf(sourceId))
            db.delete("library_sources", "source_id=?", arrayOf(sourceId))
            db.setTransactionSuccessful()
            return removed
        } finally {
            db.endTransaction()
        }
    }

    fun clearLibrary(): RemovedLibraryItems {
        val removed = RemovedLibraryItems().apply { clearQueue = true }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.query("tracks", arrayOf("track_id", "uri"), null, null, null, null, null)
                .use { tracks ->
                    while (tracks.moveToNext()) removed.add(tracks.getString(0), tracks.getString(1))
                }
            removed.sources += sources(db)
            db.delete("favorites", null, null)
            db.delete("playlist_tracks", null, null)
            db.delete("track_sources", null, null)
            db.delete("tracks", null, null)
            db.delete("excluded_tracks", null, null)
            db.delete("library_sources", null, null)
            db.setTransactionSuccessful()
            return removed
        } finally {
            db.endTransaction()
        }
    }

    override fun close() = database.close()

    private fun removeTrack(track: Track, createExclusion: Boolean): RemovedLibraryItems {
        val removed = RemovedLibraryItems()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            if (createExclusion) insertExclusion(db, ExcludedTrack.from(track, origin(db, track.trackId)))
            deleteCollectionsAndTrack(db, track.trackId)
            removed.add(track.trackId, track.uri)
            db.setTransactionSuccessful()
            return removed
        } finally {
            db.endTransaction()
        }
    }

    private fun deleteCollectionsAndTrack(db: SQLiteDatabase, trackId: String) {
        val args = arrayOf(trackId)
        db.delete("favorites", "track_id=?", args)
        db.delete("playlist_tracks", "track_id=?", args)
        db.delete("track_sources", "track_id=?", args)
        db.delete("tracks", "track_id=?", args)
    }

    private fun insertExclusion(db: SQLiteDatabase, exclusion: ExcludedTrack) {
        val values = ContentValues().apply {
            put("identity_key", exclusion.identityKey)
            put("source_id", exclusion.sourceId)
            put("document_id", exclusion.documentId)
            put("uri", exclusion.uri)
            put("file_size", exclusion.fileSize)
            put("last_modified", exclusion.lastModified)
            put("fingerprint", exclusion.fingerprint)
            put("removed_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("excluded_tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun origin(db: SQLiteDatabase, trackId: String): TrackOrigin? = db.query(
        "track_sources",
        null,
        "track_id=?",
        arrayOf(trackId),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            TrackOrigin(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))
        } else {
            null
        }
    }

    private fun source(db: SQLiteDatabase, sourceId: String): LibrarySource? = db.query(
        "library_sources",
        null,
        "source_id=?",
        arrayOf(sourceId),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) source(cursor) else null }

    private fun sources(db: SQLiteDatabase): ArrayList<LibrarySource> {
        val result = ArrayList<LibrarySource>()
        db.query("library_sources", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) result += source(cursor)
        }
        return result
    }

    private fun source(cursor: Cursor): LibrarySource = LibrarySource(
        cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
        cursor.getString(cursor.getColumnIndexOrThrow("tree_uri")),
        cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
        cursor.getLong(cursor.getColumnIndexOrThrow("revision")),
    )

    private fun collectOwnedTracks(
        db: SQLiteDatabase,
        sourceId: String,
        removed: RemovedLibraryItems,
    ) {
        db.rawQuery(
            "SELECT tracks.track_id, tracks.uri FROM tracks " +
                "JOIN track_sources ON track_sources.track_id=tracks.track_id " +
                "WHERE track_sources.source_id=?",
            arrayOf(sourceId),
        ).use { cursor ->
            while (cursor.moveToNext()) removed.add(cursor.getString(0), cursor.getString(1))
        }
    }
}
