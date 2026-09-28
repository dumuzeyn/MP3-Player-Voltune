package com.dumuzeyn.mp3player

import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/** One full-width level frame from playback PCM, without microphone access. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackVisualizerBuffer : TeeAudioProcessor.AudioBufferSink {
    @Volatile private var levels = FloatArray(BARS)
    @Volatile private var format: AudioPcmFormat? = null
    @Volatile private var enabled = false
    @Volatile private var generation = 0
    private val fft = FloatFFT_1D(FFT_SIZE.toLong())
    private val spectrum = FloatArray(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE) { index ->
        (0.5 - 0.5 * cos(2.0 * Math.PI * index / (FFT_SIZE - 1))).toFloat()
    }
    private var bandEdges = IntArray(BARS + 1)

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            generation++
            levels = FloatArray(BARS)
        }
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        format = runCatching { AudioPcmFormat(sampleRateHz, channelCount, encoding) }.getOrNull()
        bandEdges = IntArray(BARS + 1) { index ->
            val minFrequency = 45.0
            val maxFrequency = minOf(16_000.0, sampleRateHz * 0.45)
            val frequency = minFrequency * (maxFrequency / minFrequency).pow(index.toDouble() / BARS)
            (frequency * FFT_SIZE / sampleRateHz).toInt().coerceIn(2, FFT_SIZE / 2 - 1)
        }
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
        val startPosition = buffer.position()
        spectrum.fill(0f)
        val count = minOf(frames, FFT_SIZE)
        var totalPower = 0.0
        for (index in 0 until count) {
            pcm.position(startPosition + index * current.frameBytes)
            var mono = 0f
            repeat(current.channels) { mono += current.sample(pcm) }
            mono /= current.channels
            totalPower += mono * mono
            spectrum[index] = mono * window[index]
        }
        val rms = sqrt(totalPower / count).toFloat()
        if (rms < 0.001f) {
            if (enabled && currentGeneration == generation) levels = FloatArray(BARS)
            return
        }
        fft.realForward(spectrum)
        val frameLevels = FloatArray(BARS) { bar ->
            val first = bandEdges[bar]
            val last = maxOf(first + 1, bandEdges[bar + 1])
            var power = 0.0
            for (bin in first until last) {
                val real = spectrum[bin * 2].toDouble()
                val imaginary = spectrum[bin * 2 + 1].toDouble()
                power += real * real + imaginary * imaginary
            }
            sqrt(power / (last - first)).toFloat()
        }
        val peak = frameLevels.maxOrNull()?.coerceAtLeast(0.0001f) ?: 0.0001f
        val energy = (rms * 6f).coerceIn(0f, 1f)
        for (bar in frameLevels.indices) {
            frameLevels[bar] = (frameLevels[bar] / peak).coerceIn(0f, 1f).pow(0.7f) * energy
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
        private const val FFT_SIZE = 2048
        val shared = PlaybackVisualizerBuffer()
    }
}
