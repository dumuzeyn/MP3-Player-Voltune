package com.dumuzeyn.mp3player

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri

/** SQLite access for SAF sources, track ownership, and exclusion tombstones. */
class LibrarySourceStore(context: Context) : AutoCloseable {
    private val database = LibraryDatabase(context)

    fun remember(treeUri: Uri, displayName: String?, explicitImport: Boolean): LibrarySource {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val existing = find(db, treeUri)
            val revision = existing?.revision?.plus(if (explicitImport) 1L else 0L) ?: 1L
            val sourceId = LibrarySource.idFor(treeUri)
            val name = cleanName(displayName)
            val values = ContentValues().apply {
                put("source_id", sourceId)
                put("tree_uri", treeUri.toString())
                put("display_name", name)
                put("revision", revision)
                put("added_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(
                "library_sources",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            if (explicitImport) {
                db.delete("excluded_tracks", "source_id=?", arrayOf(sourceId))
            }
            db.setTransactionSuccessful()
            return LibrarySource(sourceId, treeUri.toString(), name, revision)
        } finally {
            db.endTransaction()
        }
    }

    fun list(): ArrayList<LibrarySource> {
        val result = ArrayList<LibrarySource>()
        database.readableDatabase.query(
            "library_sources",
            null,
            null,
            null,
            null,
            null,
            "display_name COLLATE NOCASE ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += source(cursor)
        }
        return result
    }

    fun find(treeUri: Uri?): LibrarySource? = find(database.readableDatabase, treeUri)

    fun findById(sourceId: String): LibrarySource? = database.readableDatabase.query(
        "library_sources",
        null,
        "source_id=?",
        arrayOf(sourceId),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) source(cursor) else null }

    fun originForTrack(trackId: String): TrackOrigin? = database.readableDatabase.query(
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

    fun exclusions(sourceId: String?): ArrayList<ExcludedTrack> {
        val result = ArrayList<ExcludedTrack>()
        val selection = sourceId?.let { "source_id=? OR source_id=''" }
        val args = sourceId?.let { arrayOf(it) }
        database.readableDatabase.query(
            "excluded_tracks",
            null,
            selection,
            args,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += ExcludedTrack(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getLong(4),
                    cursor.getLong(5),
                    cursor.getString(6),
                )
            }
        }
        return result
    }

    fun clearMatchingExclusions(track: Track) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                "excluded_tracks",
                "identity_key=? OR uri=?",
                arrayOf(TrackOrigin.uriIdentity(track.uri), track.uri),
            )
            if (track.fingerprint.isNotEmpty() && track.fileSize > 0L) {
                db.delete(
                    "excluded_tracks",
                    "fingerprint=? AND file_size=?",
                    arrayOf(track.fingerprint, track.fileSize.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun close() = database.close()

    private fun find(db: SQLiteDatabase, treeUri: Uri?): LibrarySource? {
        if (treeUri == null) return null
        return db.query(
            "library_sources",
            null,
            "tree_uri=?",
            arrayOf(treeUri.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) source(cursor) else null }
    }

    private fun source(cursor: Cursor): LibrarySource = LibrarySource(
        cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
        cursor.getString(cursor.getColumnIndexOrThrow("tree_uri")),
        cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
        cursor.getLong(cursor.getColumnIndexOrThrow("revision")),
    )

    private fun cleanName(name: String?): String {
        val value = name.orEmpty().replace('\n', ' ').replace('\r', ' ').trim()
        if (value.isEmpty()) return "Music folder"
        return value.take(160).trim()
    }
}
