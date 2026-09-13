package com.dumuzeyn.mp3player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlin.math.ceil

internal data class AudioPcmFormat(val sampleRate: Int, val channels: Int, val encoding: Int) {
    val bytesPerSample = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> error("Unsupported PCM encoding: $encoding")
    }
    val frameBytes = channels * bytesPerSample
    init { require(sampleRate in 1..384000 && channels in 1..32) }
    fun sample(pcm: ByteBuffer): Float {
        val value = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> ((pcm.get().toInt() and 255) - 128) / 128f
            AudioFormat.ENCODING_PCM_16BIT -> pcm.short / 32768f
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val low = pcm.get().toInt() and 255
                val middle = pcm.get().toInt() and 255
                val high = pcm.get().toInt()
                (low or (middle shl 8) or (high shl 16)) / 8388608f
            }
            AudioFormat.ENCODING_PCM_32BIT -> pcm.int / 2147483648f
            else -> pcm.float
        }
        return if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    }
}

/** Streams decoded buffers; consumers must finish reading before returning. */
internal class AudioPcmDecoder(context: Context) {
    private val context = context.applicationContext
    fun decode(uri: String, startMs: Long, endMs: Long, cancelled: () -> Boolean,
        consume: (AudioPcmFormat, ByteBuffer, Long) -> Unit) {
        require(startMs >= 0 && endMs > startMs && endMs <= AudioEditClip.MAX_TIME_MS)
        if (cancelled()) throw CancellationException()
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Audio track missing")
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            if (startMs > 0) extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder.configure(format, null, null, 0)
            decoder.start()
            read(extractor, decoder, format, startMs * 1000, endMs * 1000, cancelled, consume)
        } finally {
            try { decoder?.stop() } catch (_: RuntimeException) { }
            try { decoder?.release() } finally { extractor.release() }
        }
    }

    private fun read(extractor: MediaExtractor, decoder: MediaCodec, initial: MediaFormat,
        startUs: Long, endUs: Long, cancelled: () -> Boolean,
        consume: (AudioPcmFormat, ByteBuffer, Long) -> Unit) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var pcmFormat = pcmFormat(initial)
        var frames = 0L
        var lastOutput = SystemClock.elapsedRealtime()
        val deadline = lastOutput + 120000
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            val now = SystemClock.elapsedRealtime()
            check(now < deadline && now - lastOutput < 10000) { "Audio decoding timed out" }
            if (!inputDone) {
                val index = decoder.dequeueInputBuffer(10000)
                if (index >= 0) {
                    val input = checkNotNull(decoder.getInputBuffer(index))
                    input.clear()
                    val time = extractor.sampleTime
                    val size = extractor.readSampleData(input, 0)
                    // Negative AAC priming timestamps are valid input, not EOF.
                    inputDone = size < 0 || time >= endUs
                    decoder.queueInputBuffer(index, 0, if (inputDone) 0 else size,
                        if (inputDone) 0 else time, if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    if (!inputDone) extractor.advance()
                }
            }
            val index = decoder.dequeueOutputBuffer(info, 10000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) pcmFormat = pcmFormat(decoder.outputFormat)
            else if (index >= 0) {
                try {
                    val output = decoder.getOutputBuffer(index)
                    if (output != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        lastOutput = SystemClock.elapsedRealtime()
                        val count = info.size / pcmFormat.frameBytes
                        fun frameAt(time: Long) = ceil((time - info.presentationTimeUs) * pcmFormat.sampleRate / 1000000.0)
                            .toInt().coerceIn(0, count)
                        val first = frameAt(startUs)
                        // Codec timestamps are rounded to microseconds. Do not let rounding
                        // at the two selection edges add an extra PCM frame to the result.
                        val budget = ceil((endUs - startUs) * pcmFormat.sampleRate / 1000000.0).toLong()
                        val last = minOf(frameAt(endUs), first + (budget - frames).coerceIn(0, count.toLong()).toInt())
                        if (last > first) {
                            output.limit(info.offset + last * pcmFormat.frameBytes)
                            output.position(info.offset + first * pcmFormat.frameBytes)
                            consume(pcmFormat, output.slice().order(ByteOrder.LITTLE_ENDIAN),
                                info.presentationTimeUs + first * 1000000L / pcmFormat.sampleRate)
                            frames += last - first
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } finally { decoder.releaseOutputBuffer(index, false) }
            }
        }
        check(frames > 0) { "No decoded samples in selection" }
    }

    private fun pcmFormat(format: MediaFormat) = AudioPcmFormat(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
            format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT)
}
