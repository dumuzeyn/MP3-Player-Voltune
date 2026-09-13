package com.dumuzeyn.mp3player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.io.File
import java.nio.ByteOrder
import kotlin.math.sqrt

internal object ExportAudioProbe {
    fun rms(file: File): Double {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var samples = 0L
            var squares = 0.0
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!inputEnded) {
                    val index = decoder.dequeueInputBuffer(1000)
                    if (index >= 0) {
                        val buffer = checkNotNull(decoder.getInputBuffer(index))
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            inputEnded = true
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 1000)
                if (index >= 0) {
                    val output = checkNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.nativeOrder())
                    output.position(info.offset)
                    output.limit(info.offset + info.size)
                    while (output.remaining() >= 2) {
                        val value = output.short.toDouble()
                        squares += value * value
                        samples++
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        check(samples > 0)
                        return sqrt(squares / samples)
                    }
                }
            }
            error("Decoding exported audio timed out")
        } finally {
            codec?.release()
            extractor.release()
        }
    }
}
