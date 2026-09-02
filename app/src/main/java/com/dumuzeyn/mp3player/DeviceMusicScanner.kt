package com.dumuzeyn.mp3player

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Reads the system audio index and returns only high-confidence music entries. */
object DeviceMusicScanner {
    @JvmStatic
    fun scan(
        context: Context,
        knownUris: MutableSet<String>,
        exclusions: ExcludedTrackIndex,
    ): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val requestGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
            MediaStore.Audio.Media.DURATION + " >= ?"
        var cursor: Cursor? = null
        try {
            cursor = try {
                resolver.query(
                    collection,
                    projection(requestGenre),
                    selection,
                    arrayOf("20000"),
                    MediaStore.Audio.Media.DATE_ADDED + " DESC",
                )
            } catch (unsupportedGenreColumn: IllegalArgumentException) {
                if (!requestGenre) throw unsupportedGenreColumn
                resolver.query(
                    collection,
                    projection(false),
                    selection,
                    arrayOf("20000"),
                    MediaStore.Audio.Media.DATE_ADDED + " DESC",
                )
            }
            while (cursor?.moveToNext() == true) {
                val row = cursor
                val id = longValue(row, MediaStore.Audio.Media._ID)
                val uri = ContentUris.withAppendedId(collection, id)
                val uriText = uri.toString()
                if (uriText in knownUris) continue
                if (exclusions.containsIdentity(TrackOrigin.uriIdentity(uriText))) continue
                trackFromCursor(context, row, uri)?.let { track ->
                    knownUris += uriText
                    tracks += track
                }
            }
        } catch (error: RuntimeException) {
            VoltuneLog.failure("device_music_scan_failed", error)
        } finally {
            cursor?.close()
        }
        TrackStore.sort(tracks)
        return tracks
    }

    private fun trackFromCursor(context: Context, cursor: Cursor, uri: Uri): Track? {
        var title = textValue(cursor, MediaStore.Audio.Media.TITLE)
        val displayName = textValue(cursor, MediaStore.Audio.Media.DISPLAY_NAME)
        val artist = cleanUnknown(textValue(cursor, MediaStore.Audio.Media.ARTIST), "Unknown artist")
        val album = cleanUnknown(textValue(cursor, MediaStore.Audio.Media.ALBUM), "Unknown album")
        val duration = longValue(cursor, MediaStore.Audio.Media.DURATION)
        val candidate = DeviceAudioClassifier.Candidate(
            booleanValue(cursor, MediaStore.Audio.Media.IS_MUSIC),
            booleanValue(cursor, "is_recording"),
            booleanValue(cursor, MediaStore.Audio.Media.IS_PODCAST),
            booleanValue(cursor, "is_audiobook"),
            booleanValue(cursor, MediaStore.Audio.Media.IS_RINGTONE),
            booleanValue(cursor, MediaStore.Audio.Media.IS_ALARM),
            booleanValue(cursor, MediaStore.Audio.Media.IS_NOTIFICATION),
            duration,
            textValue(cursor, "relative_path"),
            displayName,
            title,
            artist,
            album,
        )
        if (!DeviceAudioClassifier.shouldAutoImport(candidate)) return null
        if (title.isEmpty()) title = displayName.substringBeforeLast('.', displayName)
        if (title.isEmpty()) title = "Song"
        val dateAdded = longValue(cursor, MediaStore.Audio.Media.DATE_ADDED) * 1_000L
        val modified = longValue(cursor, MediaStore.Audio.Media.DATE_MODIFIED) * 1_000L
        val indexed = Track(
            TrackIdentity.fromLegacyUri(uri.toString()),
            uri.toString(),
            title,
            artist,
            album,
            artist,
            textValue(cursor, "genre"),
            longValue(cursor, MediaStore.Audio.Media.YEAR).toInt(),
            (longValue(cursor, MediaStore.Audio.Media.TRACK) % 1_000L).toInt(),
            0,
            duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            longValue(cursor, MediaStore.Audio.Media.SIZE),
            modified,
            "",
            0,
            0,
            dateAdded.takeIf { it > 0L } ?: System.currentTimeMillis(),
            0L,
            0L,
        )
        return if (GenreNormalizer.isUnknown(indexed.genre)) {
            TrackStore.refreshMetadata(context, indexed)
        } else {
            indexed
        }
    }

    private fun projection(includeGenre: Boolean): Array<String> = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.DATE_ADDED)
        add(MediaStore.Audio.Media.DATE_MODIFIED)
        add(MediaStore.Audio.Media.DISPLAY_NAME)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        add(MediaStore.Audio.Media.IS_MUSIC)
        add(MediaStore.Audio.Media.IS_PODCAST)
        add(MediaStore.Audio.Media.IS_RINGTONE)
        add(MediaStore.Audio.Media.IS_ALARM)
        add(MediaStore.Audio.Media.IS_NOTIFICATION)
        if (includeGenre) add("genre")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("relative_path")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("is_recording")
            add("is_audiobook")
        }
    }.toTypedArray()

    private fun cleanUnknown(value: String, fallback: String): String =
        value.takeUnless { it.isEmpty() || it.equals("<unknown>", ignoreCase = true) } ?: fallback

    private fun booleanValue(cursor: Cursor, column: String): Boolean =
        cursor.getColumnIndex(column).let { it >= 0 && !cursor.isNull(it) && cursor.getInt(it) != 0 }

    private fun longValue(cursor: Cursor, column: String): Long =
        cursor.getColumnIndex(column).let {
            if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else 0L
        }

    private fun textValue(cursor: Cursor, column: String): String =
        cursor.getColumnIndex(column).let {
            if (it >= 0 && !cursor.isNull(it)) cursor.getString(it)?.trim().orEmpty() else ""
        }
}
