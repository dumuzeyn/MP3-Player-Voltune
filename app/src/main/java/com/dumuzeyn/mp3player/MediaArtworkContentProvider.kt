package com.dumuzeyn.mp3player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Exposes embedded cover bytes by opaque media ID without exposing the audio URI. */
class MediaArtworkContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uri.pathSegments.size != 2 || uri.pathSegments[0] != "artwork") {
            throw FileNotFoundException("Unsupported artwork request")
        }
        val appContext = context?.applicationContext
            ?: throw FileNotFoundException("Provider is unavailable")
        val mediaId = uri.pathSegments[1]
        val track = LibraryDatabase(appContext).use { database ->
            database.loadTracks().firstOrNull { MediaItemMapper.matchesMediaId(it, mediaId) }
        } ?: throw FileNotFoundException("Unknown artwork")

        val directory = File(appContext.cacheDir, "media-library-artwork").apply { mkdirs() }
        val cached = File(directory, "${mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.cover")
        if (!cached.isFile || cached.length() == 0L) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, track.asUri())
                val bytes = retriever.embeddedPicture
                    ?: throw FileNotFoundException("Artwork is unavailable")
                cached.writeBytes(bytes)
            } catch (error: SecurityException) {
                throw FileNotFoundException("Artwork access was denied").apply { initCause(error) }
            } finally {
                retriever.release()
            }
        }
        return ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
