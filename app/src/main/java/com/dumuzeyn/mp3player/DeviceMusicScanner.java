package com.dumuzeyn.mp3player;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the system audio index and returns only high-confidence music entries. */
final class DeviceMusicScanner {
    private DeviceMusicScanner() {
    }

    static ArrayList<Track> scan(Context context, Set<String> knownUris,
            ExcludedTrackIndex exclusions) {
        ArrayList<Track> tracks = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        boolean requestGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        String[] projection = projection(requestGenre);
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " >= ?";
        Cursor cursor = null;
        try {
            try {
                cursor = resolver.query(collection, projection, selection,
                        new String[]{"20000"}, MediaStore.Audio.Media.DATE_ADDED + " DESC");
            } catch (IllegalArgumentException unsupportedGenreColumn) {
                if (!requestGenre) {
                    throw unsupportedGenreColumn;
                }
                cursor = resolver.query(collection, projection(false), selection,
                        new String[]{"20000"}, MediaStore.Audio.Media.DATE_ADDED + " DESC");
            }
            while (cursor != null && cursor.moveToNext()) {
                long id = longValue(cursor, MediaStore.Audio.Media._ID);
                Uri uri = ContentUris.withAppendedId(collection, id);
                if (knownUris.contains(uri.toString())) {
                    continue;
                }
                if (exclusions.containsIdentity(TrackOrigin.uriIdentity(uri.toString()))) {
                    continue;
                }
                Track track = trackFromCursor(context, cursor, uri);
                if (track != null) {
                    knownUris.add(uri.toString());
                    tracks.add(track);
                }
            }
        } catch (RuntimeException error) {
            VoltuneLog.failure("device_music_scan_failed", error);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        TrackStore.sort(tracks);
        return tracks;
    }

    private static Track trackFromCursor(Context context, Cursor cursor, Uri uri) {
        String title = textValue(cursor, MediaStore.Audio.Media.TITLE);
        String displayName = textValue(cursor, MediaStore.Audio.Media.DISPLAY_NAME);
        String artist = cleanUnknown(textValue(cursor, MediaStore.Audio.Media.ARTIST),
                "Unknown artist");
        String album = cleanUnknown(textValue(cursor, MediaStore.Audio.Media.ALBUM),
                "Unknown album");
        long duration = longValue(cursor, MediaStore.Audio.Media.DURATION);
        DeviceAudioClassifier.Candidate candidate = new DeviceAudioClassifier.Candidate(
                booleanValue(cursor, MediaStore.Audio.Media.IS_MUSIC),
                booleanValue(cursor, "is_recording"),
                booleanValue(cursor, MediaStore.Audio.Media.IS_PODCAST),
                booleanValue(cursor, "is_audiobook"),
                booleanValue(cursor, MediaStore.Audio.Media.IS_RINGTONE),
                booleanValue(cursor, MediaStore.Audio.Media.IS_ALARM),
                booleanValue(cursor, MediaStore.Audio.Media.IS_NOTIFICATION),
                duration, textValue(cursor, "relative_path"), displayName, title,
                artist, album);
        if (!DeviceAudioClassifier.shouldAutoImport(candidate)) {
            return null;
        }
        if (title.isEmpty()) {
            title = stripExtension(displayName);
        }
        title = title.isEmpty() ? "Song" : title;
        long dateAdded = longValue(cursor, MediaStore.Audio.Media.DATE_ADDED) * 1000L;
        long modified = longValue(cursor, MediaStore.Audio.Media.DATE_MODIFIED) * 1000L;
        int trackNumber = (int) (longValue(cursor, MediaStore.Audio.Media.TRACK) % 1000L);
        int year = (int) longValue(cursor, MediaStore.Audio.Media.YEAR);
        Track indexed = new Track(TrackIdentity.fromLegacyUri(uri.toString()), uri.toString(), title,
                artist, album, artist, textValue(cursor, "genre"), year, trackNumber, 0,
                (int) Math.min(Integer.MAX_VALUE, duration),
                longValue(cursor, MediaStore.Audio.Media.SIZE), modified, "", 0, 0,
                dateAdded > 0L ? dateAdded : System.currentTimeMillis(), 0L, 0L);
        return GenreNormalizer.isUnknown(indexed.genre)
                ? TrackStore.refreshMetadata(context, indexed) : indexed;
    }

    private static String[] projection(boolean includeGenre) {
        List<String> columns = new ArrayList<>();
        columns.add(MediaStore.Audio.Media._ID);
        columns.add(MediaStore.Audio.Media.TITLE);
        columns.add(MediaStore.Audio.Media.ARTIST);
        columns.add(MediaStore.Audio.Media.ALBUM);
        columns.add(MediaStore.Audio.Media.DURATION);
        columns.add(MediaStore.Audio.Media.SIZE);
        columns.add(MediaStore.Audio.Media.DATE_ADDED);
        columns.add(MediaStore.Audio.Media.DATE_MODIFIED);
        columns.add(MediaStore.Audio.Media.DISPLAY_NAME);
        columns.add(MediaStore.Audio.Media.TRACK);
        columns.add(MediaStore.Audio.Media.YEAR);
        columns.add(MediaStore.Audio.Media.IS_MUSIC);
        columns.add(MediaStore.Audio.Media.IS_PODCAST);
        columns.add(MediaStore.Audio.Media.IS_RINGTONE);
        columns.add(MediaStore.Audio.Media.IS_ALARM);
        columns.add(MediaStore.Audio.Media.IS_NOTIFICATION);
        if (includeGenre) {
            columns.add("genre");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns.add("relative_path");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            columns.add("is_recording");
            columns.add("is_audiobook");
        }
        return columns.toArray(new String[0]);
    }

    private static String cleanUnknown(String value, String fallback) {
        return value.isEmpty() || "<unknown>".equalsIgnoreCase(value) ? fallback : value;
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static boolean booleanValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) && cursor.getInt(index) != 0;
    }

    private static long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getLong(index) : 0L;
    }

    private static String textValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        String value = index >= 0 && !cursor.isNull(index) ? cursor.getString(index) : "";
        return value == null ? "" : value.trim();
    }
}
