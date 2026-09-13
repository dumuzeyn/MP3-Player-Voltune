package com.dumuzeyn.mp3player

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

/** Transactional import boundary that rechecks sources and exclusions at commit time. */
class LibraryImportStore(context: Context) : AutoCloseable {
    private val database = LibraryDatabase(context)

    fun session(source: LibrarySource): SourceScanSession = SourceScanSession(
        source,
        ExcludedTrackIndex(loadExclusions(database.readableDatabase, source.sourceId)),
    )

    fun commitSource(
        session: SourceScanSession,
        discovered: List<DiscoveredTrack>,
    ): ArrayList<Track> {
        val accepted = ArrayList<Track>()
        val existing = database.loadTracks()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            if (!sourceRevisionMatches(db, session.source)) return accepted
            val exclusions = ExcludedTrackIndex(loadExclusions(db, session.source.sourceId))
            val acceptedUris = HashSet<String>()
            for (item in discovered) {
                if (!acceptedUris.add(item.track.uri) ||
                    exclusions.contains(item.identityKey, item.track)
                ) {
                    continue
                }
                val sameLocation = TrackDuplicatePolicy.sameLocationIndex(existing, item.track)
                val acceptedTrack = when {
                    sameLocation >= 0 -> TrackDuplicatePolicy.withStableIdentity(
                        existing[sameLocation],
                        item.track,
                    ).also { existing[sameLocation] = it }
                    TrackDuplicatePolicy.duplicateIndex(existing, item.track) >= 0 -> continue
                    else -> item.track.also(existing::add)
                }
                upsert(db, acceptedTrack)
                val origin = ContentValues().apply {
                    put("track_id", acceptedTrack.trackId)
                    put("source_id", session.source.sourceId)
                    put("document_id", item.documentId)
                    put("identity_key", item.identityKey)
                }
                db.insertWithOnConflict(
                    "track_sources",
                    null,
                    origin,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                accepted += acceptedTrack
            }
            db.setTransactionSuccessful()
            return accepted
        } finally {
            db.endTransaction()
        }
    }

    fun commitStandalone(discovered: List<Track>, explicitImport: Boolean): ArrayList<Track> {
        val accepted = ArrayList<Track>()
        val existing = database.loadTracks()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val exclusions = ExcludedTrackIndex(loadExclusions(db, null))
            val acceptedUris = HashSet<String>()
            for (track in discovered) {
                if (!acceptedUris.add(track.uri)) continue
                val identity = TrackOrigin.uriIdentity(track.uri)
                if (explicitImport) {
                    clearMatching(db, track)
                } else if (exclusions.contains(identity, track)) {
                    continue
                }
                val sameLocation = TrackDuplicatePolicy.sameLocationIndex(existing, track)
                val acceptedTrack = when {
                    sameLocation >= 0 -> TrackDuplicatePolicy.withStableIdentity(
                        existing[sameLocation],
                        track,
                    ).also { existing[sameLocation] = it }
                    TrackDuplicatePolicy.duplicateIndex(existing, track) >= 0 -> continue
                    else -> track.also(existing::add)
                }
                upsert(db, acceptedTrack)
                accepted += acceptedTrack
            }
            db.setTransactionSuccessful()
            return accepted
        } finally {
            db.endTransaction()
        }
    }

    override fun close() = database.close()

    private fun upsert(db: SQLiteDatabase, track: Track) {
        val values = LibraryDatabase.trackValues(track).apply {
            put("metadata_revision", LibraryMaintenanceController.METADATA_REVISION)
        }
        db.insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun sourceRevisionMatches(db: SQLiteDatabase, source: LibrarySource): Boolean =
        db.query(
            "library_sources",
            arrayOf("revision"),
            "source_id=?",
            arrayOf(source.sourceId),
            null,
            null,
            null,
            "1",
        ).use { it.moveToFirst() && it.getLong(0) == source.revision }

    private fun loadExclusions(db: SQLiteDatabase, sourceId: String?): ArrayList<ExcludedTrack> {
        val result = ArrayList<ExcludedTrack>()
        val selection = sourceId?.let { "source_id=? OR source_id=''" }
        val args = sourceId?.let { arrayOf(it) }
        db.query("excluded_tracks", null, selection, args, null, null, null).use { cursor ->
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

    private fun clearMatching(db: SQLiteDatabase, track: Track) {
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
    }
}
