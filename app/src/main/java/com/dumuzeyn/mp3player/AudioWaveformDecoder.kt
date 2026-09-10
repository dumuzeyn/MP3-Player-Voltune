package com.dumuzeyn.mp3player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

internal class AudioWaveformDecoder(context: Context) {
    private val context = context.applicationContext

    fun decode(uri: String, durationMs: Long, cancelled: () -> Boolean): AudioWaveform {
        require(durationMs in 1..AudioEditClip.MAX_TIME_MS)
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Audio track missing")
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder.configure(format, null, null, 0)
            decoder.start()
            return read(extractor, decoder, format, durationMs, cancelled)
        } finally {
            try { decoder?.stop() } catch (_: RuntimeException) { }
            try { decoder?.release() } finally { extractor.release() }
        }
    }

    private fun read(extractor: MediaExtractor, decoder: MediaCodec, format: MediaFormat,
        durationMs: Long, cancelled: () -> Boolean): AudioWaveform {
        val envelope = AudioWaveformAccumulator(durationMs * 1000)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var frames = 0L
        var lastOutput = SystemClock.elapsedRealtime()
        val deadline = lastOutput + 120_000L
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            val now = SystemClock.elapsedRealtime()
            check(now < deadline && now - lastOutput < 10_000) { "Waveform decoding timed out" }
            if (!inputDone) {
                val index = decoder.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val input = checkNotNull(decoder.getInputBuffer(index))
                    val time = extractor.sampleTime
                    val size = extractor.readSampleData(input, 0)
                    // AAC priming packets can precede zero without indicating end of stream.
                    inputDone = size < 0 || time >= durationMs * 1000
                    decoder.queueInputBuffer(index, 0, if (inputDone) 0 else size,
                        if (inputDone) 0 else time, if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    if (!inputDone) extractor.advance()
                }
            }
            val index = decoder.dequeueOutputBuffer(info, 10_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val output = decoder.outputFormat
                sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            } else if (index >= 0) {
                try {
                    val output = decoder.getOutputBuffer(index)
                    if (output != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(sampleRate > 0 && channels in 1..32)
                        check(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        val pcm = output.slice().order(ByteOrder.LITTLE_ENDIAN)
                        val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        var frame = 0L
                        while (pcm.remaining() >= channels * bytes) {
                            var peak = 0f
                            repeat(channels) {
                                val value = if (bytes == 4) pcm.float else pcm.short / 32768f
                                if (value.isFinite()) peak = maxOf(peak, kotlin.math.abs(value))
                            }
                            envelope.add(info.presentationTimeUs + frame * 1_000_000 / sampleRate, peak)
                            frame++
                        }
                        frames += frame
                        lastOutput = SystemClock.elapsedRealtime()
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } finally { decoder.releaseOutputBuffer(index, false) }
            }
        }
        check(frames > 0) { "No decoded samples" }
        return envelope.finish()
    }
}
