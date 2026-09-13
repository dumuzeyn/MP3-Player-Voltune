package com.dumuzeyn.mp3player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded float PCM resampling with Media3's Sonic engine; no playback instance. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AudioPcmResampler(private val context: Context) {
    fun decode(clip: AudioEditClip, rate: Int, stereo: Boolean, cancelled: () -> Boolean,
        consume: (ByteBuffer, Int) -> Unit) {
        val sonic = SonicAudioProcessor()
        var source: AudioPcmFormat? = null
        var channels = 0
        var converted = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        try {
            AudioPcmDecoder(context).decode(clip.uri, clip.startMs, clip.endMs, cancelled) { format, pcm, _ ->
                if (source == null) {
                    require(format.channels <= 2) { "Processing requires mono or stereo audio" }
                    source = format
                    channels = if (stereo) 2 else format.channels
                    sonic.setOutputSampleRateHz(rate)
                    sonic.configure(AudioProcessor.AudioFormat(format.sampleRate, channels, C.ENCODING_PCM_FLOAT))
                    sonic.flush(AudioProcessor.StreamMetadata.DEFAULT)
                }
                require(source == format) { "PCM format changed" }
                val count = pcm.remaining() / format.frameBytes
                val bytes = count * channels * 4
                if (converted.capacity() < bytes) converted = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
                converted.clear()
                repeat(count) {
                    val left = format.sample(pcm)
                    val right = if (format.channels == 2) format.sample(pcm) else left
                    converted.putFloat(left)
                    if (channels == 2) converted.putFloat(right)
                }
                converted.flip()
                if (sonic.isActive) {
                    sonic.queueInput(converted)
                    consume(sonic.output.order(ByteOrder.nativeOrder()), channels)
                } else consume(converted, channels)
            }
            if (sonic.isActive) {
                sonic.queueEndOfStream()
                while (!sonic.isEnded) consume(sonic.output.order(ByteOrder.nativeOrder()), channels)
            }
        } finally { sonic.reset() }
    }
}
