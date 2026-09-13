package com.dumuzeyn.mp3player

import android.content.Context
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class FolderGrouping(context: Context? = null) {
    private val resolver = context?.applicationContext?.contentResolver
    private val mediaStoreFolders by lazy(::loadMediaStoreFolders)

    fun group(tracks: List<Track>): Map<String, ArrayList<Track>> {
        val groups = LinkedHashMap<String, ArrayList<Track>>()
        tracks.forEach { track ->
            val name = mediaStoreFolders[track.uri] ?: folderName(track.uri)
            groups.getOrPut(name, ::ArrayList) += track
        }
        return groups
    }

    private fun loadMediaStoreFolders(): Map<String, String> {
        val contentResolver = resolver ?: return emptyMap()
        val column = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        return runCatching {
            val result = HashMap<String, String>()
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID, column),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.isNull(0) || cursor.isNull(1)) continue
                    val value = cursor.getString(1)?.trim().orEmpty()
                    val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        value.trimEnd('/', '\\').substringAfterLast('/').trim().ifEmpty { null }
                    } else {
                        File(value).parentFile?.name?.trim()?.ifEmpty { null }
                    }
                    if (folder != null) {
                        result[ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()] =
                            folder
                    }
                }
            }
            result
        }.getOrElse { emptyMap() }
    }

    companion object {
        private const val UNKNOWN = "Unknown folder"

        @JvmStatic
        fun folderName(rawUri: String?): String = runCatching {
            if (rawUri.isNullOrBlank()) return UNKNOWN
            var path = rawUri.substringBefore('?')
            val scheme = path.indexOf("://")
            if (scheme >= 0) {
                val firstPath = path.indexOf('/', scheme + 3)
                path = if (firstPath >= 0) path.substring(firstPath + 1) else ""
            }
            path = URLDecoder.decode(path, StandardCharsets.UTF_8.name())
            if (path.isBlank()) return UNKNOWN
            val separator = maxOf(path.lastIndexOf('/'), path.lastIndexOf(':'))
            val parent = if (separator > 0) path.substring(0, separator) else path
            val parentSeparator = maxOf(parent.lastIndexOf('/'), parent.lastIndexOf(':'))
            parent.substring(parentSeparator + 1).trim().ifEmpty { UNKNOWN }
        }.getOrDefault(UNKNOWN)
    }
}
