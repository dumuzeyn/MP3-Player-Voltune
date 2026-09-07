package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors
import kotlin.math.max

@UnstableApi
internal class MediaArtworkProvider(context: Context) : BitmapLoader {
    private val context = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = LruCache<String, Bitmap>(8)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = submit { decode(data) }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        cache.get(uri.toString())?.let { cached ->
            return SettableFuture.create<Bitmap>().apply { set(cached) }
        }
        return submit {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                decode(retriever.embeddedPicture).also { bitmap ->
                    cache.put(uri.toString(), bitmap)
                }
            } finally {
                retriever.release()
            }
        }
    }

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    fun close() {
        executor.shutdownNow()
        cache.evictAll()
    }

    private fun decode(data: ByteArray?): Bitmap {
        val bytes = data?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Artwork is unavailable")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (
            bounds.outWidth / sample > MAX_ARTWORK_SIZE * 2 ||
            bounds.outHeight / sample > MAX_ARTWORK_SIZE * 2
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = max(1, sample) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("Artwork cannot be decoded")
    }

    private fun submit(task: () -> Bitmap): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                future.set(task())
            } catch (error: Exception) {
                future.setException(error)
            }
        }
        return future
    }

    private companion object {
        const val MAX_ARTWORK_SIZE = 768
    }
}
