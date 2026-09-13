@file:Suppress("DEPRECATION")

package com.dumuzeyn.mp3player

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Displays only decoded raster pixels; image metadata, links and scripts are never executed. */
@SuppressLint("ViewConstructor")
internal class BackgroundMediaView(
    context: Context,
    private val mediaUri: String,
    blurPercent: Int,
    fallbackColor: Int,
) : ImageView(context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val blurPercent = blurPercent.coerceIn(0, 100)
    private var legacyMovie: Movie? = null
    private var movieStartedAt = 0L
    private var uiActive = true

    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(fallbackColor)
        load()
    }

    fun setUiActive(active: Boolean) {
        uiActive = active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Api28Decoder.setAnimatedRunning(drawable, active)
        }
        if (active) {
            movieStartedAt = SystemClock.uptimeMillis()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Api28Decoder.setAnimatedRunning(drawable, false)
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val movie = legacyMovie
        if (movie == null || !uiActive) {
            super.onDraw(canvas)
            return
        }
        val duration = max(100, movie.duration())
        movie.setTime(((SystemClock.uptimeMillis() - movieStartedAt) % duration).toInt())
        val scale = max(width.toFloat() / movie.width(), height.toFloat() / movie.height())
        val left = (width - movie.width() * scale) * 0.5f
        val top = (height - movie.height() * scale) * 0.5f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        movie.draw(canvas, 0f, 0f)
        canvas.restore()
        postInvalidateDelayed(LEGACY_FRAME_DELAY_MS)
    }

    private fun load() {
        val key = "$mediaUri#$blurPercent#${Build.VERSION.SDK_INT}"
        val cached = bitmapCache.get(key)
        if (cached != null && !cached.isRecycled) {
            setImageBitmap(cached)
            applyModernBlur()
            return
        }
        val reference = WeakReference(this)
        decoder.execute {
            try {
                val decoded = decode(key) ?: return@execute
                if (reference.get() == null) return@execute
                mainHandler.post {
                    val liveTarget = reference.get() ?: return@post
                    if (liveTarget.mediaUri != mediaUri) return@post
                    liveTarget.display(decoded)
                }
            } catch (_: Exception) {
                // The validated URI may have been revoked or removed after it was selected.
            }
        }
    }

    private fun decode(cacheKey: String): DecodedMedia? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Api28Decoder.decode(appContext, mediaUri, blurPercent, cacheKey)
        }
        appContext.contentResolver.openInputStream(Uri.parse(mediaUri)).use { input ->
            val data = readBounded(input)
            if (blurPercent == 0 && isGif(data)) {
                Movie.decodeByteArray(data, 0, data.size)?.let { return DecodedMedia.movie(it) }
            }
            var bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
            bitmap = scaleDown(bitmap)
            bitmap = blurLegacy(appContext, bitmap, blurPercent)
            bitmapCache.put(cacheKey, bitmap)
            return DecodedMedia.bitmap(bitmap)
        }
    }

    private fun display(decoded: DecodedMedia) {
        decoded.movie?.let {
            legacyMovie = it
            movieStartedAt = SystemClock.uptimeMillis()
            invalidate()
            return
        }
        setImageDrawable(decoded.drawable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Api28Decoder.setAnimatedRunning(decoded.drawable, uiActive)
        }
        applyModernBlur()
    }

    private fun applyModernBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = if (blurPercent == 0) 0f else max(1f, blurPercent * 0.35f)
            Api31Blur.apply(this, radius)
        }
    }

    private data class DecodedMedia(val drawable: Drawable?, val movie: Movie?) {
        companion object {
            fun bitmap(bitmap: Bitmap) = DecodedMedia(BitmapDrawable(null, bitmap), null)
            fun drawable(drawable: Drawable) = DecodedMedia(drawable, null)
            fun movie(movie: Movie) = DecodedMedia(null, movie)
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private object Api28Decoder {
        fun decode(
            context: Context,
            mediaUri: String,
            blurPercent: Int,
            cacheKey: String,
        ): DecodedMedia {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(mediaUri))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blurPercent > 0) {
                var bitmap = ImageDecoder.decodeBitmap(source) { imageDecoder, info, _ ->
                    imageDecoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    setTargetSize(imageDecoder, info)
                }
                bitmap = scaleDown(bitmap)
                bitmap = blurLegacy(context, bitmap, blurPercent)
                bitmapCache.put(cacheKey, bitmap)
                return DecodedMedia.bitmap(bitmap)
            }
            val drawable = ImageDecoder.decodeDrawable(source) { imageDecoder, info, _ ->
                setTargetSize(imageDecoder, info)
            }
            return DecodedMedia.drawable(drawable)
        }

        private fun setTargetSize(decoder: ImageDecoder, info: ImageDecoder.ImageInfo) {
            val width = info.size.width
            val height = info.size.height
            val largest = max(width, height)
            if (largest > MAX_DECODE_SIZE) {
                val scale = MAX_DECODE_SIZE.toFloat() / largest
                decoder.setTargetSize(
                    max(1, (width * scale).roundToInt()),
                    max(1, (height * scale).roundToInt()),
                )
            }
        }

        fun setAnimatedRunning(drawable: Drawable?, running: Boolean) {
            val animated = drawable as? AnimatedImageDrawable ?: return
            if (running) animated.start() else animated.stop()
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private object Api31Blur {
        fun apply(view: View, radius: Float) {
            view.setRenderEffect(
                if (radius == 0f) {
                    null
                } else {
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                },
            )
        }
    }

    private companion object {
        const val MAX_DECODE_SIZE = 2048
        const val MAX_INPUT_BYTES = 32 * 1024 * 1024
        const val LEGACY_FRAME_DELAY_MS = 32L
        val decoder = Executors.newFixedThreadPool(2)
        val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                max(1, value.byteCount / 1024)
        }

        fun blurLegacy(context: Context, source: Bitmap, amount: Int): Bitmap {
            if (amount <= 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return source
            val mutable = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
            var renderScript: RenderScript? = null
            var input: Allocation? = null
            var output: Allocation? = null
            var blur: ScriptIntrinsicBlur? = null
            try {
                renderScript = RenderScript.create(context.applicationContext)
                input = Allocation.createFromBitmap(renderScript, mutable)
                output = Allocation.createTyped(renderScript, input.type)
                blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
                blur.setRadius(max(0.1f, min(25f, amount / 4f)))
                blur.setInput(input)
                blur.forEach(output)
                output.copyTo(mutable)
                if (mutable !== source) source.recycle()
                return mutable
            } catch (_: RuntimeException) {
                mutable.recycle()
                return source
            } finally {
                blur?.destroy()
                input?.destroy()
                output?.destroy()
                renderScript?.destroy()
            }
        }

        fun scaleDown(bitmap: Bitmap): Bitmap {
            val largest = max(bitmap.width, bitmap.height)
            if (largest <= MAX_DECODE_SIZE) return bitmap
            val scale = MAX_DECODE_SIZE.toFloat() / largest
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).roundToInt()),
                max(1, (bitmap.height * scale).roundToInt()),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            return scaled
        }

        fun readBounded(input: InputStream?): ByteArray {
            checkNotNull(input) { "Image is unavailable" }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                check(total <= MAX_INPUT_BYTES) { "Image is too large" }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        fun isGif(data: ByteArray): Boolean = data.size >= 6 &&
            data[0] == 'G'.code.toByte() &&
            data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte()
    }
}
