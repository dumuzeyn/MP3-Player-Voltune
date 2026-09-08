package com.dumuzeyn.mp3player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** Decodes short representative ranges and never retains a full PCM track. */
internal class AudioFeatureExtractor(context: Context) {
    private val context = context.applicationContext

    @Throws(Exception::class)
    fun analyze(track: Track, shouldYield: YieldSignal): DoubleArray {
        val probe = probe(track)
        val accumulator = AudioFeatureAccumulator(probe.sampleRate)
        for (startUs in representativeStarts(probe.durationUs)) {
            if (shouldYield.shouldYield()) throw AnalysisInterruptedException()
            accumulator.beginSegment()
            decodeRange(
                track,
                startUs,
                startUs + SEGMENT_US,
                probe.sampleRate,
                probe.channels,
                accumulator,
                shouldYield,
            )
        }
        val result = accumulator.finish()
        if (result.size != TrackAudioProfile.FEATURE_COUNT) {
            throw IllegalStateException("insufficient_audio")
        }
        return result
    }

    @Throws(Exception::class)
    private fun probe(track: Track): Probe {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            val format = selectAudioTrack(extractor)
                ?: throw IllegalArgumentException("audio_track_missing")
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
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                track.durationMs * 1_000L
            }
            return Probe(sampleRate, channels, max(SEGMENT_US, duration))
        } finally {
            extractor.release()
        }
    }

    @Throws(Exception::class)
    private fun decodeRange(
        track: Track,
        startUs: Long,
        endUs: Long,
        sampleRate: Int,
        channels: Int,
        accumulator: AudioFeatureAccumulator,
        shouldYield: YieldSignal,
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            val format = selectAudioTrack(extractor)
                ?: throw IllegalArgumentException("audio_track_missing")
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("audio_mime_missing")
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            decode(
                extractor,
                decoder,
                startUs,
                endUs,
                sampleRate,
                channels,
                accumulator,
                shouldYield,
            )
        } finally {
            release(decoder)
            extractor.release()
        }
    }

    class AnalysisInterruptedException : Exception()

    fun interface YieldSignal {
        fun shouldYield(): Boolean
    }

    private data class Probe(
        val sampleRate: Int,
        val channels: Int,
        val durationUs: Long,
    )

    companion object {
        private const val SEGMENT_US = 10_000_000L

        @JvmStatic
        fun representativeStarts(durationUs: Long): ArrayList<Long> {
            val duration = max(SEGMENT_US, durationUs)
            val last = max(0L, duration - SEGMENT_US)
            val middle = max(0L, duration / 2L - SEGMENT_US / 2L)
            return ArrayList<Long>().apply {
                addDistinct(this, 0L)
                addDistinct(this, min(middle, last))
                addDistinct(this, last)
            }
        }

        private fun addDistinct(values: ArrayList<Long>, value: Long) {
            if (!values.contains(value)) values.add(value)
        }

        private fun selectAudioTrack(extractor: MediaExtractor): MediaFormat? {
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(index)
                    return format
                }
            }
            return null
        }

        @Throws(Exception::class)
        private fun decode(
            extractor: MediaExtractor,
            decoder: MediaCodec,
            startUs: Long,
            endUs: Long,
            sampleRate: Int,
            channels: Int,
            accumulator: AudioFeatureAccumulator,
            shouldYield: YieldSignal,
        ) {
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            while (!outputDone) {
                if (shouldYield.shouldYield() || Thread.currentThread().isInterrupted) {
                    throw AnalysisInterruptedException()
                }
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                        val time = extractor.sampleTime
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        if (size < 0 || time < 0L || time > endUs) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                max(0L, time),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = decoder.dequeueOutputBuffer(info, 10_000L)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output = decoder.outputFormat
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        output.containsKey(MediaFormat.KEY_PCM_ENCODING)
                    ) {
                        encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                } else if (outputIndex >= 0) {
                    val output = decoder.getOutputBuffer(outputIndex)
                    if (
                        output != null && info.size > 0 &&
                        info.presentationTimeUs >= startUs && info.presentationTimeUs <= endUs
                    ) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        accumulator.addPcm(
                            output.slice().order(ByteOrder.LITTLE_ENDIAN),
                            encoding,
                            channels,
                        )
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }

        private fun release(decoder: MediaCodec?) {
            if (decoder == null) return
            try {
                decoder.stop()
            } catch (_: RuntimeException) {
            }
            try {
                decoder.release()
            } catch (_: RuntimeException) {
            }
        }
    }
}
