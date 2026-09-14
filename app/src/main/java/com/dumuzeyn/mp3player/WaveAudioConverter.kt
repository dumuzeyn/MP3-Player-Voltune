package com.dumuzeyn.mp3player

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal object WaveAudioConverter {
    fun convert(input: File, output: File, cancelled: () -> Boolean = { false }) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val raw = File.createTempFile("voltune-pcm-", ".raw", output.parentFile)
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).first { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val sourceFormat = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(sourceFormat.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sampleRate = 0
            var channels = 0
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            BufferedOutputStream(raw.outputStream()).use { pcm ->
                while (!outputEnded) {
                    check(!cancelled()) { "Conversion cancelled" }
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = checkNotNull(decoder.getInputBuffer(index)).apply { clear() }
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
                    when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        }
                        else -> if (index >= 0) {
                            if (info.size > 0) {
                                val buffer = checkNotNull(decoder.getOutputBuffer(index)).duplicate().apply {
                                    position(info.offset)
                                    limit(info.offset + info.size)
                                    order(ByteOrder.nativeOrder())
                                }
                                writePcm16(buffer, encoding, pcm)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            check(sampleRate > 0 && channels > 0 && raw.length() > 0)
            writeWave(raw, output, sampleRate, channels)
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
            raw.delete()
        }
    }

    private fun writePcm16(buffer: java.nio.ByteBuffer, encoding: Int, output: OutputStream) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val converted = ByteArray(8192)
            while (buffer.remaining() >= 4) {
                var count = 0
                while (buffer.remaining() >= 4 && count + 2 <= converted.size) {
                    val value = (buffer.float.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort().toInt()
                    converted[count++] = (value and 0xff).toByte()
                    converted[count++] = (value ushr 8 and 0xff).toByte()
                }
                output.write(converted, 0, count)
            }
        } else {
            check(encoding == AudioFormat.ENCODING_PCM_16BIT)
            val bytes = ByteArray(8192)
            while (buffer.hasRemaining()) {
                val count = minOf(buffer.remaining(), bytes.size)
                buffer.get(bytes, 0, count)
                output.write(bytes, 0, count)
            }
        }
    }

    private fun writeWave(raw: File, output: File, sampleRate: Int, channels: Int) {
        val size = raw.length()
        check(size <= 0xfffffff0L)
        BufferedOutputStream(output.outputStream()).use { wave ->
            wave.write("RIFF".toByteArray(Charsets.US_ASCII))
            wave.writeIntLe((36 + size).toInt())
            wave.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            wave.writeIntLe(16)
            wave.writeShortLe(1)
            wave.writeShortLe(channels)
            wave.writeIntLe(sampleRate)
            wave.writeIntLe(sampleRate * channels * 2)
            wave.writeShortLe(channels * 2)
            wave.writeShortLe(16)
            wave.write("data".toByteArray(Charsets.US_ASCII))
            wave.writeIntLe(size.toInt())
            raw.inputStream().use { it.copyTo(wave) }
        }
    }

    private fun OutputStream.writeIntLe(value: Int) {
        repeat(4) { shift -> write(value ushr (shift * 8) and 0xff) }
    }

    private fun OutputStream.writeShortLe(value: Int) {
        repeat(2) { shift -> write(value ushr (shift * 8) and 0xff) }
    }
}
