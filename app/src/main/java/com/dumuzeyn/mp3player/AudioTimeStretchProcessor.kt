package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AudioTimeStretchProcessor(private val context: Context) {
    fun process(input: File, sourceDurationMs: Long, targetDurationMs: Long, output: File,
        cancelled: () -> Boolean, progress: (Int) -> Unit): Long {
        require(sourceDurationMs > 0 && targetDurationMs > 0)
        val speed = sourceDurationMs.toFloat() / targetDurationMs
        require(speed in 0.25f..4f) { "Vocal duration difference is too large" }
        val sonic = SonicAudioProcessor().apply {
            setSpeed(speed)
            setPitch(1f)
            configure(AudioProcessor.AudioFormat(RATE, 2, C.ENCODING_PCM_FLOAT))
            flush(AudioProcessor.StreamMetadata.DEFAULT)
        }
        var frames = 0L
        val targetFrames = targetDurationMs * RATE / 1000
        fun write(buffer: ByteBuffer, writer: PcmWaveWriter) {
            buffer.order(ByteOrder.nativeOrder())
            while (buffer.remaining() >= 8) {
                if (cancelled()) throw CancellationException()
                writer.sample(buffer.float)
                writer.sample(buffer.float)
                frames++
            }
            progress((frames * 100 / targetFrames.coerceAtLeast(1)).toInt().coerceAtMost(99))
        }
        try {
            PcmWaveWriter(output, RATE, 2).use { writer ->
                val clip = AudioEditClip(uri = Uri.fromFile(input).toString(), title = "vocals",
                    sourceDurationMs = sourceDurationMs)
                AudioPcmResampler(context).decode(clip, RATE, true, cancelled) { pcm, _ ->
                    if (cancelled()) throw CancellationException()
                    sonic.queueInput(pcm)
                    write(sonic.output, writer)
                }
                sonic.queueEndOfStream()
                while (!sonic.isEnded) write(sonic.output, writer)
            }
            progress(100)
            return frames * 1000 / RATE
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally { sonic.reset() }
    }

    companion object { private const val RATE = 44100 }
}
