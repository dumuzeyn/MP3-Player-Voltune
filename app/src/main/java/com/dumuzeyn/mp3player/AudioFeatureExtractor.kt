package com.dumuzeyn.mp3player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.util.concurrent.CancellationException

internal data class AudioMusicalAnalysis(val features: DoubleArray, val key: MusicalKey?) {
    val bpm get() = features.getOrElse(TrackAudioProfile.BPM) { 0.0 }
}

/** Decodes bounded representative ranges through the same PCM reader as editing. */
internal class AudioFeatureExtractor(context: Context) {
    private val context = context.applicationContext
    private val decoder = AudioPcmDecoder(context)

    @Throws(Exception::class)
    fun analyze(track: Track, shouldYield: YieldSignal): DoubleArray =
        measure(track.uri, 0, duration(track), shouldYield, false).features

    fun musical(clip: AudioEditClip, shouldYield: YieldSignal): AudioMusicalAnalysis =
        measure(clip.uri, clip.startMs, clip.endMs, shouldYield, true)

    private fun measure(uri: String, startMs: Long, endMs: Long, shouldYield: YieldSignal,
        includeKey: Boolean): AudioMusicalAnalysis {
        var accumulator: AudioFeatureAccumulator? = null
        var key: MusicalKeyEstimator? = null
        var sampleRate = 0
        try {
            for (relativeUs in representativeStarts((endMs - startMs) * 1000)) {
                if (shouldYield.shouldYield()) throw AnalysisInterruptedException()
                val from = startMs + relativeUs / 1000
                var first = true
                decoder.decode(uri, from, minOf(endMs, from + 10000), { shouldYield.shouldYield() }) { format, pcm, _ ->
                    if (accumulator == null) {
                        sampleRate = format.sampleRate
                        accumulator = AudioFeatureAccumulator(sampleRate)
                        if (includeKey) key = MusicalKeyEstimator(sampleRate)
                    }
                    require(format.sampleRate == sampleRate) { "Sample rate changes within selection" }
                    val features = checkNotNull(accumulator)
                    if (first) { features.beginSegment(); key?.beginSegment(); first = false }
                    while (pcm.remaining() >= format.frameBytes) {
                        var mono = 0f
                        repeat(format.channels) { mono += format.sample(pcm) / format.channels }
                        features.addSample(mono)
                        key?.addSample(mono)
                    }
                }
            }
        } catch (_: CancellationException) { throw AnalysisInterruptedException() }
        val result = checkNotNull(accumulator).finish()
        check(result.size == TrackAudioProfile.FEATURE_COUNT) { "insufficient_audio" }
        return AudioMusicalAnalysis(result, key?.finish())
    }

    private fun duration(track: Track): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    val ms = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000
                        else track.durationMs.toLong()
                    return ms.coerceIn(1, AudioEditClip.MAX_TIME_MS)
                }
            }
            error("audio_track_missing")
        } finally { extractor.release() }
    }

    class AnalysisInterruptedException : Exception()
    fun interface YieldSignal { fun shouldYield(): Boolean }

    companion object {
        private const val SEGMENT_US = 10000000L
        @JvmStatic fun representativeStarts(durationUs: Long): ArrayList<Long> {
            val duration = maxOf(SEGMENT_US, durationUs)
            val last = duration - SEGMENT_US
            return ArrayList(listOf(0L, minOf(maxOf(0L, duration / 2 - SEGMENT_US / 2), last), last).distinct())
        }
    }
}
