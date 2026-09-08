package com.dumuzeyn.mp3player

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.util.LinkedHashMap

/** Owns persisted audio profiles and assignments without exposing database details to UI. */
internal class SoundProfileStore(context: Context) : Closeable {
    private val database = LibraryDatabase(context)

    @Synchronized
    fun loadProfiles(): LinkedHashMap<String, TrackAudioProfile> {
        val result = LinkedHashMap<String, TrackAudioProfile>()
        database.readableDatabase.query(
            "audio_profiles",
            null,
            null,
            null,
            null,
            null,
            "track_id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val profile = profile(cursor)
                result[profile.trackId] = profile
            }
        }
        return result
    }

    @Synchronized
    fun loadGroups(): ArrayList<SoundGroup> {
        val builders = LinkedHashMap<String, GroupBuilder>()
        database.readableDatabase.query(
            "sound_groups",
            null,
            null,
            null,
            null,
            null,
            "position ASC, group_id ASC",
        ).use { groups ->
            while (groups.moveToNext()) {
                val id = groups.string("group_id")
                builders[id] = GroupBuilder(
                    id,
                    groups.string("name_ru"),
                    groups.string("name_en"),
                    TrackAudioProfile.decodeFeatures(groups.string("centroid")),
                )
            }
        }
        database.readableDatabase.query(
            "audio_profiles",
            arrayOf("track_id", "group_id"),
            "state=? AND group_id<>''",
            arrayOf(SoundAnalysisState.ANALYZED.name),
            null,
            null,
            "track_id ASC",
        ).use { assignments ->
            while (assignments.moveToNext()) {
                builders[assignments.getString(1)]?.trackIds?.add(assignments.getString(0))
            }
        }
        val result = ArrayList<SoundGroup>()
        for (builder in builders.values) {
            if (builder.trackIds.isNotEmpty()) result.add(builder.build())
        }
        return result
    }

    @Synchronized
    fun saveProfile(profile: TrackAudioProfile) {
        database.writableDatabase.insertWithOnConflict(
            "audio_profiles",
            null,
            values(profile),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun mark(track: Track, state: SoundAnalysisState, error: String?) {
        val pending = TrackAudioProfile.pending(track, state)
        saveProfile(
            TrackAudioProfile(
                pending.trackId,
                pending.analysisVersion,
                pending.fileSize,
                pending.lastModified,
                pending.fingerprint,
                state,
                pending.features,
                "",
                error,
                System.currentTimeMillis(),
            ),
        )
    }

    @Synchronized
    fun replaceGroups(groups: List<SoundGroup>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("sound_groups", null, null)
            db.update(
                "audio_profiles",
                ContentValues().apply { put("group_id", "") },
                null,
                null,
            )
            groups.forEachIndexed { position, group ->
                val groupValues = ContentValues().apply {
                    put("group_id", group.id)
                    put("name_ru", group.nameRussian)
                    put("name_en", group.nameEnglish)
                    put("centroid", encode(group.centroid))
                    put("position", position)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertOrThrow("sound_groups", null, groupValues)
                val assignment = ContentValues().apply { put("group_id", group.id) }
                for (trackId in group.trackIds) {
                    db.update(
                        "audio_profiles",
                        assignment,
                        "track_id=?",
                        arrayOf(trackId),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun assign(trackId: String, groupId: String?) {
        val values = ContentValues().apply { put("group_id", groupId.orEmpty()) }
        database.writableDatabase.update(
            "audio_profiles",
            values,
            "track_id=?",
            arrayOf(trackId),
        )
    }

    @Synchronized
    fun pruneEmptyGroups() {
        database.writableDatabase.execSQL(
            "DELETE FROM sound_groups WHERE group_id " +
                "NOT IN (SELECT DISTINCT group_id FROM audio_profiles WHERE group_id<>'')",
        )
    }

    @Synchronized
    fun clearAnalysis() {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("sound_groups", null, null)
            db.delete("audio_profiles", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun close() {
        database.close()
    }

    private class GroupBuilder(
        private val id: String,
        private val russian: String,
        private val english: String,
        private val centroid: DoubleArray,
    ) {
        val trackIds = ArrayList<String>()

        fun build(): SoundGroup = SoundGroup(id, russian, english, centroid, trackIds)
    }

    companion object {
        private fun values(profile: TrackAudioProfile): ContentValues = ContentValues().apply {
            put("track_id", profile.trackId)
            put("analysis_version", profile.analysisVersion)
            put("file_size", profile.fileSize)
            put("last_modified", profile.lastModified)
            put("fingerprint", profile.fingerprint)
            put("state", profile.state.name)
            put("vector", profile.encodeFeatures())
            put("group_id", profile.groupId)
            put("error", profile.error)
            put("updated_at", profile.updatedAt)
        }

        private fun profile(cursor: Cursor): TrackAudioProfile = TrackAudioProfile(
            cursor.string("track_id"),
            cursor.getInt(cursor.getColumnIndexOrThrow("analysis_version")),
            cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
            cursor.getLong(cursor.getColumnIndexOrThrow("last_modified")),
            cursor.string("fingerprint"),
            SoundAnalysisState.parse(cursor.string("state")),
            TrackAudioProfile.decodeFeatures(cursor.string("vector")),
            cursor.string("group_id"),
            cursor.string("error"),
            cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
        )

        private fun encode(values: DoubleArray): String = TrackAudioProfile(
            "",
            TrackAudioProfile.ANALYSIS_VERSION,
            0L,
            0L,
            "",
            SoundAnalysisState.ANALYZED,
            values,
            "",
            "",
            0L,
        ).encodeFeatures()

        private fun Cursor.string(column: String): String =
            getString(getColumnIndexOrThrow(column))
    }
}
