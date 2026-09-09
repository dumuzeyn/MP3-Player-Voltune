package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/** Performs bounded EBU R128-style analysis and caches content-versioned results. */
internal class TrackLoudnessNormalizer(context: Context) {
    fun interface ProgressListener {
        fun onProgress(
            completed: Int,
            total: Int,
            errors: Int,
            finished: Boolean,
            cancelled: Boolean,
        )
    }

    private val context = context.applicationContext
    private val cache: SharedPreferences =
        this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val pending = Collections.synchronizedSet(HashSet<String>())
    private val cancelRequested = AtomicBoolean()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var batchRunning = false

    fun cachedGainDb(track: Track?): Float {
        if (!isEnabled || track == null) return 0f
        val result = cachedResult(track) ?: return 0f
        val settings = context.getSharedPreferences(EqualizerController.PREFS, Context.MODE_PRIVATE)
        val mode = LoudnessLevelingMode.fromPreference(
            settings.getString(LoudnessLevelingMode.PREFERENCE, null),
            settings.getBoolean(REDUCE_ONLY, false),
        )
        return mode.gainDb(
            result.integratedLufs,
            result.peakDbfs,
            cache.getFloat(REFERENCE_TARGET_PREFIX + mode.name, mode.fallbackTarget),
        )
    }

    fun updateReferenceTracks(tracks: List<Track>) {
        val snapshot = tracks.toList()
        executor.execute {
            val keys = snapshot.mapTo(HashSet()) { RESULT_PREFIX + cacheKeyFor(it) }
            cache.edit().putStringSet(REFERENCE_KEYS, keys).apply()
            updateReferenceLevels()
        }
    }

    private fun updateReferenceLevels() {
        val levels = cache.getStringSet(REFERENCE_KEYS, emptySet()).orEmpty().mapNotNull { key ->
            cache.getString(key, null)?.substringBefore(',')?.toFloatOrNull()
        }
        val editor = cache.edit()
        LoudnessLevelingMode.entries.forEach { mode ->
            editor.putFloat(REFERENCE_TARGET_PREFIX + mode.name, mode.referenceTarget(levels))
        }
        editor.apply()
    }

    fun prefetch(queue: List<Track>?, currentIndex: Int) {
        if (!isEnabled || queue.isNullOrEmpty()) return
        for (offset in 0 until min(3, queue.size)) {
            prefetch(queue[(max(0, currentIndex) + offset) % queue.size])
        }
    }

    fun analyzeLibrary(tracks: List<Track>?, listener: ProgressListener?) {
        if (batchRunning) {
            notifyProgress(listener, 0, tracks?.size ?: 0, errorCount(tracks), false, false)
            return
        }
        val source = if (tracks == null) ArrayList() else ArrayList(tracks)
        cancelRequested.set(false)
        batchRunning = true
        executor.execute {
            if (!cache.contains(REFERENCE_KEYS)) {
                cache.edit().putStringSet(REFERENCE_KEYS,
                    source.mapTo(HashSet()) { RESULT_PREFIX + cacheKeyFor(it) }).apply()
            }
            var completed = 0
            var errors = 0
            for (track in source) {
                if (cancelRequested.get() || Thread.currentThread().isInterrupted) break
                if (cachedResult(track) == null && !analyzeAndCache(track)) errors++
                completed++
                notifyProgress(listener, completed, source.size, errors, false, false)
            }
            val cancelled = cancelRequested.get() || completed < source.size
            updateReferenceLevels()
            batchRunning = false
            notifyProgress(listener, completed, source.size, errors, true, cancelled)
        }
    }

    fun cancelAnalysis() {
        cancelRequested.set(true)
    }

    fun clearCache() {
        cancelAnalysis()
        val references = cache.getStringSet(REFERENCE_KEYS, emptySet()).orEmpty().toSet()
        cache.edit().clear().putStringSet(REFERENCE_KEYS, references).commit()
    }

    fun analyzedCount(tracks: List<Track>?): Int =
        tracks?.count { cachedResult(it) != null } ?: 0

    fun errorCount(tracks: List<Track>?): Int {
        val failed = failedTrackIds()
        return tracks?.count { it.trackId in failed } ?: 0
    }

    fun failedTrackIds(): Set<String> = cache.all.keys
        .filterTo(HashSet()) { it.startsWith(ERROR_PREFIX) }
        .mapTo(HashSet()) { it.substring(ERROR_PREFIX.length) }

    fun isBatchRunning(): Boolean = batchRunning

    fun release() {
        cancelAnalysis()
        executor.shutdownNow()
        pending.clear()
    }

    val isEnabled: Boolean
        get() = context
            .getSharedPreferences(EqualizerController.PREFS, Context.MODE_PRIVATE)
            .getBoolean(VolumeLevelingController.ENABLED, false)

    private fun prefetch(track: Track?) {
        if (track == null || cachedResult(track) != null || !pending.add(track.trackId)) return
        executor.execute {
            try {
                analyzeAndCache(track)
                updateReferenceLevels()
            } finally {
                pending.remove(track.trackId)
            }
        }
    }

    private fun analyzeAndCache(track: Track?): Boolean {
        if (track == null || cancelRequested.get()) return false
        return try {
            val result = analyze(track)
            if (result == null) {
                recordError(track, "no_audio_result")
                false
            } else {
                val encoded = "${result.integratedLufs},${result.peakDbfs}"
                cache.edit()
                    .putString(RESULT_PREFIX + cacheKeyFor(track), encoded)
                    .remove(ERROR_PREFIX + track.trackId)
                    .apply()
                VoltuneLog.info("r128_analyzed")
                true
            }
        } catch (error: Exception) {
            recordError(track, error.javaClass.simpleName)
            VoltuneLog.failure("r128_analysis_failed", error)
            false
        }
    }

    private fun analyze(track: Track): LoudnessAnalysisResult? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            val format = selectAudioTrack(extractor) ?: return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44_100
            }
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            return decode(extractor, decoder, sampleRate, channels)
        } finally {
            decoder?.let {
                try {
                    it.stop()
                } catch (_: RuntimeException) {
                }
                try {
                    it.release()
                } catch (_: RuntimeException) {
                }
            }
            extractor.release()
        }
    }

    private fun decode(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        sampleRate: Int,
        channels: Int,
    ): LoudnessAnalysisResult? {
        val info = MediaCodec.BufferInfo()
        val meter = R128LoudnessMeter(sampleRate, channels)
        var inputDone = false
        var outputDone = false
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        while (!outputDone && !cancelRequested.get() && !Thread.currentThread().isInterrupted) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(BUFFER_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val input = decoder.getInputBuffer(inputIndex)
                    val size = input?.let { extractor.readSampleData(it, 0) } ?: -1
                    val sampleTime = extractor.sampleTime
                    if (size < 0 || sampleTime > MAX_ANALYSIS_US) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            max(0L, sampleTime),
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, size, sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = decoder.dequeueOutputBuffer(info, BUFFER_TIMEOUT_US)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = decoder.outputFormat
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                ) {
                    pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                }
            } else if (outputIndex >= 0) {
                decoder.getOutputBuffer(outputIndex)?.let { output ->
                    if (info.size > 0) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        meter.add(output.slice().order(ByteOrder.LITTLE_ENDIAN), pcmEncoding)
                    }
                }
                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                decoder.releaseOutputBuffer(outputIndex, false)
            }
        }
        return if (cancelRequested.get()) null else meter.result()
    }

    private fun cachedResult(track: Track): LoudnessAnalysisResult? {
        val key = RESULT_PREFIX + cacheKeyFor(track)
        val encoded = cache.getString(key, "")
        if (encoded.isNullOrEmpty()) return null
        return try {
            val values = encoded.split(',', limit = 3)
            require(values.size == 2) { "invalid result" }
            val lufs = values[0].toFloat()
            val peak = values[1].toFloat()
            require(lufs.isFinite() && peak.isFinite()) { "non-finite result" }
            LoudnessAnalysisResult(lufs, peak)
        } catch (_: RuntimeException) {
            cache.edit().remove(key).apply()
            null
        }
    }

    private fun recordError(track: Track?, category: String?) {
        if (track != null && !cancelRequested.get()) {
            cache.edit().putString(
                ERROR_PREFIX + track.trackId,
                category ?: "unknown",
            ).apply()
        }
    }

    private fun notifyProgress(
        listener: ProgressListener?,
        completed: Int,
        total: Int,
        errors: Int,
        finished: Boolean,
        cancelled: Boolean,
    ) {
        listener ?: return
        mainHandler.post {
            listener.onProgress(completed, total, errors, finished, cancelled)
        }
    }

    companion object {
        const val ALGORITHM_VERSION = 3
        const val PREFS = "track_loudness_cache"
        const val REDUCE_ONLY = "reduce_only"
        const val TARGET_LUFS = "target_lufs"
        private const val REFERENCE_KEYS = "reference_keys"
        private const val REFERENCE_TARGET_PREFIX = "reference_target_"
        private const val ANALYSIS_PROFILE = "kweight-400ms-100ms-gates70-10-peak4"
        private const val RESULT_PREFIX = "r128_result_"
        private const val ERROR_PREFIX = "r128_error_"
        private const val MAX_ANALYSIS_US = 15L * 60L * 1_000_000L
        private const val BUFFER_TIMEOUT_US = 10_000L

        @JvmStatic
        fun cacheKeyFor(track: Track?): String = cacheKeyFor(track, ALGORITHM_VERSION)

        @JvmStatic
        fun cacheKeyFor(track: Track?, algorithmVersion: Int): String {
            if (track == null) return "none"
            val identity = track.trackId + '|' + track.fileSize + '|' + track.lastModified +
                '|' + algorithmVersion + '|' + ANALYSIS_PROFILE
            return hash(identity)
        }

        private fun selectAudioTrack(extractor: MediaExtractor): MediaFormat? {
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    extractor.selectTrack(index)
                    return format
                }
            }
            return null
        }

        private fun hash(value: String): String = try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
            buildString(32) {
                for (index in 0 until 16) {
                    append(String.format(Locale.ROOT, "%02x", digest[index].toInt() and 0xff))
                }
            }
        } catch (_: Exception) {
            Integer.toHexString(value.hashCode())
        }
    }
}
