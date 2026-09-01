package com.dumuzeyn.mp3player;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns the persisted audio profiles and assignments without leaking database details to UI. */
final class SoundProfileStore implements Closeable {
    private final LibraryDatabase database;

    SoundProfileStore(Context context) {
        database = new LibraryDatabase(context);
    }

    synchronized LinkedHashMap<String, TrackAudioProfile> loadProfiles() {
        LinkedHashMap<String, TrackAudioProfile> result = new LinkedHashMap<>();
        Cursor cursor = database.getReadableDatabase().query("audio_profiles", null,
                null, null, null, null, "track_id ASC");
        try {
            while (cursor.moveToNext()) {
                TrackAudioProfile profile = profile(cursor);
                result.put(profile.trackId, profile);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    synchronized ArrayList<SoundGroup> loadGroups() {
        LinkedHashMap<String, GroupBuilder> builders = new LinkedHashMap<>();
        Cursor groups = database.getReadableDatabase().query("sound_groups", null,
                null, null, null, null, "position ASC, group_id ASC");
        try {
            while (groups.moveToNext()) {
                String id = groups.getString(groups.getColumnIndexOrThrow("group_id"));
                builders.put(id, new GroupBuilder(id,
                        groups.getString(groups.getColumnIndexOrThrow("name_ru")),
                        groups.getString(groups.getColumnIndexOrThrow("name_en")),
                        TrackAudioProfile.decodeFeatures(groups.getString(
                                groups.getColumnIndexOrThrow("centroid")))));
            }
        } finally {
            groups.close();
        }
        Cursor assignments = database.getReadableDatabase().query("audio_profiles",
                new String[]{"track_id", "group_id"},
                "state=? AND group_id<>''", new String[]{SoundAnalysisState.ANALYZED.name()},
                null, null, "track_id ASC");
        try {
            while (assignments.moveToNext()) {
                GroupBuilder builder = builders.get(assignments.getString(1));
                if (builder != null) {
                    builder.trackIds.add(assignments.getString(0));
                }
            }
        } finally {
            assignments.close();
        }
        ArrayList<SoundGroup> result = new ArrayList<>();
        for (GroupBuilder builder : builders.values()) {
            if (!builder.trackIds.isEmpty()) {
                result.add(builder.build());
            }
        }
        return result;
    }

    synchronized void saveProfile(TrackAudioProfile profile) {
        database.getWritableDatabase().insertWithOnConflict("audio_profiles", null,
                values(profile), SQLiteDatabase.CONFLICT_REPLACE);
    }

    synchronized void mark(Track track, SoundAnalysisState state, String error) {
        TrackAudioProfile pending = TrackAudioProfile.pending(track, state);
        saveProfile(new TrackAudioProfile(pending.trackId, pending.analysisVersion,
                pending.fileSize, pending.lastModified, pending.fingerprint, state,
                pending.features, "", error, System.currentTimeMillis()));
    }

    synchronized void replaceGroups(List<SoundGroup> groups) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sound_groups", null, null);
            ContentValues clear = new ContentValues();
            clear.put("group_id", "");
            db.update("audio_profiles", clear, null, null);
            int position = 0;
            for (SoundGroup group : groups) {
                ContentValues groupValues = new ContentValues();
                groupValues.put("group_id", group.id);
                groupValues.put("name_ru", group.nameRussian);
                groupValues.put("name_en", group.nameEnglish);
                groupValues.put("centroid", encode(group.centroid));
                groupValues.put("position", position++);
                groupValues.put("updated_at", System.currentTimeMillis());
                db.insertOrThrow("sound_groups", null, groupValues);
                ContentValues assignment = new ContentValues();
                assignment.put("group_id", group.id);
                for (String trackId : group.trackIds) {
                    db.update("audio_profiles", assignment, "track_id=?",
                            new String[]{trackId});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    synchronized void assign(String trackId, String groupId) {
        ContentValues values = new ContentValues();
        values.put("group_id", groupId == null ? "" : groupId);
        database.getWritableDatabase().update("audio_profiles", values, "track_id=?",
                new String[]{trackId});
    }

    synchronized void pruneEmptyGroups() {
        database.getWritableDatabase().execSQL("DELETE FROM sound_groups WHERE group_id "
                + "NOT IN (SELECT DISTINCT group_id FROM audio_profiles WHERE group_id<>'')");
    }

    synchronized void clearAnalysis() {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sound_groups", null, null);
            db.delete("audio_profiles", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized void close() {
        database.close();
    }

    private static ContentValues values(TrackAudioProfile profile) {
        ContentValues values = new ContentValues();
        values.put("track_id", profile.trackId);
        values.put("analysis_version", profile.analysisVersion);
        values.put("file_size", profile.fileSize);
        values.put("last_modified", profile.lastModified);
        values.put("fingerprint", profile.fingerprint);
        values.put("state", profile.state.name());
        values.put("vector", profile.encodeFeatures());
        values.put("group_id", profile.groupId);
        values.put("error", profile.error);
        values.put("updated_at", profile.updatedAt);
        return values;
    }

    private static TrackAudioProfile profile(Cursor cursor) {
        return new TrackAudioProfile(
                cursor.getString(cursor.getColumnIndexOrThrow("track_id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("analysis_version")),
                cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
                cursor.getLong(cursor.getColumnIndexOrThrow("last_modified")),
                cursor.getString(cursor.getColumnIndexOrThrow("fingerprint")),
                SoundAnalysisState.parse(cursor.getString(cursor.getColumnIndexOrThrow("state"))),
                TrackAudioProfile.decodeFeatures(cursor.getString(
                        cursor.getColumnIndexOrThrow("vector"))),
                cursor.getString(cursor.getColumnIndexOrThrow("group_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("error")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")));
    }

    private static String encode(double[] values) {
        return new TrackAudioProfile("", TrackAudioProfile.ANALYSIS_VERSION, 0L, 0L, "",
                SoundAnalysisState.ANALYZED, values, "", "", 0L).encodeFeatures();
    }

    private static final class GroupBuilder {
        final String id;
        final String russian;
        final String english;
        final double[] centroid;
        final ArrayList<String> trackIds = new ArrayList<>();

        GroupBuilder(String id, String russian, String english, double[] centroid) {
            this.id = id;
            this.russian = russian;
            this.english = english;
            this.centroid = centroid;
        }

        SoundGroup build() {
            return new SoundGroup(id, russian, english, centroid, trackIds);
        }
    }
}
