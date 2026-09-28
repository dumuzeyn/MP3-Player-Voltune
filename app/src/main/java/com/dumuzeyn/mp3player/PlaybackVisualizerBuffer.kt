package com.dumuzeyn.mp3player

import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** One full-width level frame from playback PCM, without microphone access. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackVisualizerBuffer : TeeAudioProcessor.AudioBufferSink {
    @Volatile private var levels = FloatArray(BARS)
    @Volatile private var format: AudioPcmFormat? = null
    @Volatile private var enabled = false
    @Volatile private var generation = 0

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            generation++
            levels = FloatArray(BARS)
        }
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        format = runCatching { AudioPcmFormat(sampleRateHz, channelCount, encoding) }.getOrNull()
        generation++
        levels = FloatArray(BARS)
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!enabled) return
        val current = format ?: return
        val currentGeneration = generation
        val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frames = pcm.remaining() / current.frameBytes
        if (frames == 0) return
        val frameLevels = FloatArray(BARS)
        val startPosition = buffer.position()
        for (bar in frameLevels.indices) {
            val first = bar * frames / BARS
            val last = maxOf(first + 1, (bar + 1) * frames / BARS)
            val step = maxOf(1, (last - first) / 8)
            var power = 0.0
            var sampled = 0
            for (frame in first until last step step) {
                pcm.position(startPosition + frame * current.frameBytes)
                repeat(current.channels) {
                    val sample = current.sample(pcm).toDouble()
                    power += sample * sample
                    sampled++
                }
            }
            frameLevels[bar] = sqrt(power / sampled).toFloat().coerceIn(0f, 1f)
        }
        if (enabled && currentGeneration == generation) levels = frameLevels
    }

    fun snapshot(bars: Int): FloatArray {
        require(bars > 0)
        val frame = levels
        return FloatArray(bars) { index ->
            frame[(index.toLong() * frame.size / bars).toInt()]
        }
    }

    companion object {
        private const val BARS = 96
        val shared = PlaybackVisualizerBuffer()
    }
}
