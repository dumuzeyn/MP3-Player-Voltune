package com.dumuzeyn.mp3player

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.util.LruCache
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class CoverLoader(
    private val context: Context,
    private val mainHandler: Handler,
) {
    private val cache: LruCache<String, Bitmap>
    private val pendingTargets = LinkedHashMap<String, ArrayList<PendingTarget>>()
    private val executor = Executors.newFixedThreadPool(2)

    @Volatile
    private var diskCache: ArtworkDiskCache? = null

    @Volatile
    private var closed = false

    init {
        val maxKb = min(
            16L * 1024L,
            max(6L * 1024L, Runtime.getRuntime().maxMemory() / 1024L / 16L),
        ).toInt()
        cache = object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                max(1, value.byteCount / 1024)
        }
    }

    fun load(view: ImageView, track: Track, fallbackColor: Int) {
        load(view, track, fallbackColor, THUMB_SIZE)
    }

    fun loadSmooth(
        view: ImageView,
        track: Track,
        fallbackColor: Int,
        maxSize: Int,
        transitionDuration: Int,
    ) {
        load(view, track, fallbackColor, maxSize, true, transitionDuration)
    }

    fun loadCachedOnly(view: ImageView, track: Track, fallbackColor: Int, maxSize: Int) {
        val key = key(track, maxSize)
        view.tag = key
        var cached = cache.get(key)
        if (cached == null && maxSize != THUMB_SIZE) cached = cache.get(key(track, THUMB_SIZE))
        if (cached != null && !cached.isRecycled) {
            view.setImageBitmap(cached)
        } else {
            view.setImageDrawable(null)
            view.setBackgroundColor(fallbackColor)
        }
    }

    fun load(view: ImageView, track: Track, fallbackColor: Int, maxSize: Int) {
        load(view, track, fallbackColor, maxSize, false, 0)
    }

    fun seedFromView(view: ImageView?, track: Track?) {
        val bitmap = (view?.drawable as? BitmapDrawable)?.bitmap
        if (track != null && bitmap != null && !bitmap.isRecycled) {
            cache.put(key(track, THUMB_SIZE), bitmap)
        }
    }

    fun clear(view: ImageView?, fallbackColor: Int) {
        view ?: return
        view.tag = null
        view.setImageDrawable(null)
        view.setBackgroundColor(fallbackColor)
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        if (
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            cache.evictAll()
        } else if (
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
        ) {
            cache.trimToSize(max(1, cache.maxSize() / 2))
        }
    }

    fun close() {
        closed = true
        executor.shutdownNow()
        synchronized(pendingTargets) { pendingTargets.clear() }
        cache.evictAll()
    }

    private fun load(
        view: ImageView,
        track: Track,
        fallbackColor: Int,
        maxSize: Int,
        smooth: Boolean,
        transitionDuration: Int,
    ) {
        if (closed) return
        val key = key(track, maxSize)
        if (key == view.tag && view.drawable != null) return
        view.tag = key
        val cached = cache.get(key)
        if (cached != null && !cached.isRecycled) {
            applyBitmap(view, cached, fallbackColor, smooth, transitionDuration)
            return
        }
        val thumbnail = if (maxSize == THUMB_SIZE) null else cache.get(key(track, THUMB_SIZE))
        if (thumbnail != null && !thumbnail.isRecycled) {
            applyBitmap(view, thumbnail, fallbackColor, smooth, transitionDuration)
        } else if (!smooth || view.drawable == null) {
            view.setImageDrawable(null)
            view.setBackgroundColor(fallbackColor)
        }
        synchronized(pendingTargets) {
            pendingTargets[key]?.let { waiting ->
                waiting += PendingTarget(view, fallbackColor, smooth, transitionDuration)
                return
            }
            pendingTargets[key] = arrayListOf(
                PendingTarget(view, fallbackColor, smooth, transitionDuration),
            )
        }
        try {
            executor.execute {
                val persistentCache = diskCache()
                var loaded = persistentCache.read(key)
                if (loaded == null) {
                    loaded = read(track, maxSize)
                    if (loaded != null) persistentCache.write(key, loaded)
                }
                val bitmap = loaded
                if (closed) return@execute
                if (bitmap != null) {
                    cache.put(key, bitmap)
                    if (maxSize != THUMB_SIZE) cacheThumbnail(track, bitmap)
                }
                val targets = synchronized(pendingTargets) { pendingTargets.remove(key) }
                mainHandler.post {
                    if (closed || targets == null) return@post
                    for (pending in targets) {
                        val target = pending.view.get()
                        if (target != null && key == target.tag) {
                            if (bitmap == null) {
                                applyFallback(
                                    target,
                                    pending.fallbackColor,
                                    pending.smooth,
                                    pending.transitionDuration,
                                )
                            } else {
                                applyBitmap(
                                    target,
                                    bitmap,
                                    pending.fallbackColor,
                                    pending.smooth,
                                    pending.transitionDuration,
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            synchronized(pendingTargets) { pendingTargets.remove(key) }
        }
    }

    private fun cacheThumbnail(track: Track, fullCover: Bitmap?) {
        val key = key(track, THUMB_SIZE)
        if (cache.get(key) != null || fullCover == null || fullCover.isRecycled) return
        val width = fullCover.width
        val height = fullCover.height
        if (width <= 0 || height <= 0) return
        val scale = min(THUMB_SIZE.toFloat() / width, THUMB_SIZE.toFloat() / height)
        if (scale >= 1f) {
            cache.put(key, fullCover)
            return
        }
        cache.put(
            key,
            Bitmap.createScaledBitmap(
                fullCover,
                max(1, (width * scale).roundToInt()),
                max(1, (height * scale).roundToInt()),
                true,
            ),
        )
    }

    private fun applyBitmap(
        view: ImageView,
        bitmap: Bitmap,
        fallbackColor: Int,
        smooth: Boolean,
        transitionDuration: Int,
    ) {
        if (!smooth || transitionDuration <= 0) {
            view.setImageBitmap(bitmap)
            return
        }
        applyDrawable(
            view,
            BitmapDrawable(view.resources, bitmap),
            fallbackColor,
            transitionDuration,
        )
    }

    private fun applyFallback(
        view: ImageView,
        fallbackColor: Int,
        smooth: Boolean,
        transitionDuration: Int,
    ) {
        view.setBackgroundColor(fallbackColor)
        if (!smooth || transitionDuration <= 0 || view.drawable == null) {
            view.setImageDrawable(null)
            return
        }
        applyDrawable(view, ColorDrawable(fallbackColor), fallbackColor, transitionDuration)
    }

    private fun applyDrawable(
        view: ImageView,
        next: Drawable,
        fallbackColor: Int,
        transitionDuration: Int,
    ) {
        val previous = view.drawable ?: ColorDrawable(fallbackColor)
        val transition = TransitionDrawable(arrayOf(previous, next)).apply {
            isCrossFadeEnabled = true
        }
        view.setImageDrawable(transition)
        transition.startTransition(transitionDuration)
    }

    private fun read(track: Track, maxSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, track.asUri())
            val picture = retriever.embeddedPicture
            if (picture == null || picture.size > MAX_COVER_BYTES) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds, maxSize)
            }
            val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                ?: return null
            if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap
            val scale = min(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).roundToInt()),
                max(1, (bitmap.height * scale).roundToInt()),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            return scaled
        } catch (_: RuntimeException) {
            return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun sampleSize(options: BitmapFactory.Options, maxSize: Int): Int {
        var sample = 1
        while (
            options.outWidth / sample > maxSize * 2 ||
            options.outHeight / sample > maxSize * 2
        ) {
            sample *= 2
        }
        return max(1, sample)
    }

    private fun key(track: Track, maxSize: Int): String = track.trackId + "|" + track.uri +
        "|" + track.fileSize + "|" + track.lastModified + "|" + track.fingerprint + "#" + maxSize

    private fun diskCache(): ArtworkDiskCache {
        diskCache?.let { return it }
        synchronized(this) {
            if (diskCache == null) diskCache = ArtworkDiskCache(context)
            return checkNotNull(diskCache)
        }
    }

    private class PendingTarget(
        view: ImageView,
        val fallbackColor: Int,
        val smooth: Boolean,
        val transitionDuration: Int,
    ) {
        val view = WeakReference(view)
    }

    companion object {
        const val THUMB_SIZE = 160
        private const val MAX_COVER_BYTES = 8 * 1024 * 1024
    }
}
