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
    private val missingKeys = LinkedHashMap<String, Long>()
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
        if (cached == null || cached.isRecycled) {
            cached = diskCache().read(key)
            if (cached != null && !cached.isRecycled) cache.put(key, cached)
        }
        if (cached == null && maxSize != THUMB_SIZE) cached = cache.get(key(track, THUMB_SIZE))
        if (cached != null && !cached.isRecycled) {
            view.setImageBitmap(cached)
        } else {
            applyFallback(view, fallbackColor, false, 0)
        }
    }

    fun prefetch(tracks: List<Track>, maxSize: Int = THUMB_SIZE) {
        tracks.asSequence().distinctBy { it.trackId }.take(PREFETCH_LIMIT).forEach { track ->
            val key = key(track, maxSize)
            if (cache.get(key) != null || recentlyMissing(key)) return@forEach
            synchronized(pendingTargets) {
                if (pendingTargets.containsKey(key)) return@forEach
                pendingTargets[key] = arrayListOf()
            }
            startLoad(track, maxSize, key)
        }
    }

    fun prefetchBeforeRender(tracks: List<Track>, onComplete: () -> Unit) {
        val source = tracks.distinctBy { it.trackId }.take(VISIBLE_PREFETCH_LIMIT)
        try {
            executor.execute {
                source.forEach { track ->
                    if (closed) return@execute
                    val key = key(track, THUMB_SIZE)
                    if (cache.get(key) != null || recentlyMissing(key)) return@forEach
                    val persistentCache = diskCache()
                    val bitmap = persistentCache.read(key) ?: read(track, THUMB_SIZE)?.also {
                        persistentCache.write(key, it)
                    }
                    if (bitmap == null) rememberMissing(key) else cache.put(key, bitmap)
                }
                mainHandler.post { if (!closed) onComplete() }
            }
        } catch (_: RejectedExecutionException) {
            mainHandler.post(onComplete)
        }
    }

    fun load(view: ImageView, track: Track, fallbackColor: Int, maxSize: Int) {
        load(view, track, fallbackColor, maxSize, false, 0)
    }

    fun loadBest(view: ImageView, tracks: List<Track>, fallbackColor: Int, maxSize: Int) {
        if (tracks.isEmpty()) {
            applyFallback(view, fallbackColor, false, 0)
            return
        }
        cachedCandidate(tracks, maxSize)?.let {
            load(view, it, fallbackColor, maxSize)
            return
        }
        val groupKey = "group|" + tracks.joinToString("|") { it.trackId }
        view.tag = groupKey
        applyFallback(view, fallbackColor, false, 0)
        prefetchGroupCovers(listOf(tracks)) {
            if (view.tag != groupKey) return@prefetchGroupCovers
            cachedCandidate(tracks, maxSize)?.let { load(view, it, fallbackColor, maxSize) }
        }
    }

    fun prefetchGroupCovers(groups: List<List<Track>>, onComplete: () -> Unit = {}) {
        try {
            executor.execute {
                groups.forEach { tracks ->
                    for (track in tracks) {
                        if (closed) return@execute
                        val key = key(track, THUMB_SIZE)
                        var bitmap = cache.get(key)
                        if (bitmap == null || bitmap.isRecycled) {
                            bitmap = diskCache().read(key) ?: read(track, THUMB_SIZE)?.also {
                                diskCache().write(key, it)
                            }
                        }
                        if (bitmap != null && !bitmap.isRecycled) {
                            cache.put(key, bitmap)
                            synchronized(missingKeys) { missingKeys.remove(key) }
                            break
                        }
                    }
                }
                mainHandler.post { if (!closed) onComplete() }
            }
        } catch (_: RejectedExecutionException) {
            mainHandler.post(onComplete)
        }
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
        view.tag = key
        var cached = cache.get(key)
        if (cached == null || cached.isRecycled) {
            cached = diskCache().read(key)
            if (cached != null && !cached.isRecycled) cache.put(key, cached)
        }
        if (cached != null && !cached.isRecycled) {
            applyBitmap(view, cached, fallbackColor, smooth, transitionDuration)
            return
        }
        val thumbnail = if (maxSize == THUMB_SIZE) null else cache.get(key(track, THUMB_SIZE))
        if (thumbnail != null && !thumbnail.isRecycled) {
            applyBitmap(view, thumbnail, fallbackColor, smooth, transitionDuration)
        } else if (recentlyMissing(key)) {
            applyFallback(view, fallbackColor, smooth, transitionDuration)
            return
        } else if (!smooth || view.drawable == null) {
            applyFallback(view, fallbackColor, false, 0)
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
        startLoad(track, maxSize, key)
    }

    private fun startLoad(track: Track, maxSize: Int, key: String) {
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
                    synchronized(missingKeys) { missingKeys.remove(key) }
                    if (maxSize != THUMB_SIZE) cacheThumbnail(track, bitmap)
                } else {
                    rememberMissing(key)
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

    private fun rememberMissing(key: String) {
        synchronized(missingKeys) {
            missingKeys[key] = System.currentTimeMillis()
            while (missingKeys.size > MISSING_LIMIT) {
                missingKeys.remove(missingKeys.keys.firstOrNull() ?: break)
            }
        }
    }

    private fun recentlyMissing(key: String): Boolean = synchronized(missingKeys) {
        val time = missingKeys[key] ?: return@synchronized false
        if (System.currentTimeMillis() - time <= MISSING_RETRY_MS) return@synchronized true
        missingKeys.remove(key)
        false
    }

    private fun cachedCandidate(tracks: List<Track>, maxSize: Int): Track? {
        for (track in tracks) {
            val key = key(track, maxSize)
            var bitmap = cache.get(key)
            if (bitmap == null || bitmap.isRecycled) bitmap = diskCache().read(key)
            if (bitmap != null && !bitmap.isRecycled) {
                cache.put(key, bitmap)
                return track
            }
        }
        return null
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
        private const val PREFETCH_LIMIT = 32
        private const val VISIBLE_PREFETCH_LIMIT = 10
        private const val MISSING_LIMIT = 512
        private const val MISSING_RETRY_MS = 30_000L
        private const val MAX_COVER_BYTES = 8 * 1024 * 1024
    }
}
