package com.dumuzeyn.mp3player

import android.media.AudioFormat
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** EBU R128-style K-weighting, absolute/relative gating and inter-sample peak estimate. */
internal class R128LoudnessMeter(sampleRate: Int, channelCount: Int) {
    private val channels = max(1, channelCount)
    private val filters: Array<Array<Biquad>>
    private val previousSamples = DoubleArray(channels)
    private val frameChannelEnergy = DoubleArray(channels)
    private val energyWindow: DoubleArray
    private val stepFrames: Int
    private val blockEnergies = ArrayList<Double>()
    private var channelIndex = 0
    private var windowPosition = 0
    private var windowFrames = 0
    private var framesSinceBlock = 0
    private var rollingEnergy = 0.0
    private var peak = 0.0

    init {
        val safeRate = max(8_000, sampleRate)
        energyWindow = DoubleArray(max(1, (safeRate * 0.4f).roundToInt()))
        stepFrames = max(1, (safeRate * 0.1f).roundToInt())
        filters = Array(channels) {
            arrayOf(
                Biquad.highShelf(safeRate.toDouble(), 1_500.0, 4.0, sqrt(0.5)),
                Biquad.highPass(safeRate.toDouble(), 38.0, 0.5),
            )
        }
    }

    fun add(buffer: ByteBuffer, encoding: Int) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            while (buffer.remaining() >= 4) addSample(clamp(buffer.float.toDouble()))
        } else {
            while (buffer.remaining() >= 2) addSample(buffer.short / 32_768.0)
        }
    }

    fun addSamples(interleaved: FloatArray?) {
        if (interleaved == null) return
        for (sample in interleaved) addSample(clamp(sample.toDouble()))
    }

    fun result(): LoudnessAnalysisResult? {
        if (blockEnergies.isEmpty() || peak <= 0.0) return null
        val absolute = gated(blockEnergies, ABSOLUTE_GATE_LUFS)
        if (absolute.isEmpty()) return null
        val ungatedLoudness = loudness(mean(absolute))
        val relative = gated(absolute, ungatedLoudness - RELATIVE_GATE_LU)
        val integrated = loudness(mean(if (relative.isEmpty()) absolute else relative))
        val peakDb = 20.0 * log10(max(1.0e-12, peak))
        return LoudnessAnalysisResult(integrated.toFloat(), peakDb.toFloat())
    }

    private fun addSample(sample: Double) {
        val channel = channelIndex
        val previous = previousSamples[channel]
        for (step in 1..4) {
            val interpolated = previous + (sample - previous) * step / 4.0
            peak = max(peak, abs(interpolated))
        }
        previousSamples[channel] = sample
        val weighted = filters[channel][1].process(filters[channel][0].process(sample))
        frameChannelEnergy[channel] = weighted * weighted
        channelIndex++
        if (channelIndex >= channels) {
            channelIndex = 0
            completeFrame()
        }
    }

    private fun completeFrame() {
        var energy = 0.0
        for (channel in 0 until channels) energy += frameChannelEnergy[channel]
        if (windowFrames >= energyWindow.size) {
            rollingEnergy -= energyWindow[windowPosition]
        } else {
            windowFrames++
        }
        energyWindow[windowPosition] = energy
        rollingEnergy += energy
        windowPosition = (windowPosition + 1) % energyWindow.size
        framesSinceBlock++
        if (windowFrames == energyWindow.size && framesSinceBlock >= stepFrames) {
            blockEnergies.add(rollingEnergy / energyWindow.size)
            framesSinceBlock = 0
        }
    }

    private class Biquad(
        b0: Double,
        b1: Double,
        b2: Double,
        a0: Double,
        a1: Double,
        a2: Double,
    ) {
        private val b0 = b0 / a0
        private val b1 = b1 / a0
        private val b2 = b2 / a0
        private val a1 = a1 / a0
        private val a2 = a2 / a0
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(input: Double): Double {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        companion object {
            fun highPass(rate: Double, frequency: Double, q: Double): Biquad {
                val w0 = 2.0 * Math.PI * frequency / rate
                val cosine = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                return Biquad(
                    (1.0 + cosine) / 2.0,
                    -(1.0 + cosine),
                    (1.0 + cosine) / 2.0,
                    1.0 + alpha,
                    -2.0 * cosine,
                    1.0 - alpha,
                )
            }

            fun highShelf(
                rate: Double,
                frequency: Double,
                gainDb: Double,
                q: Double,
            ): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * Math.PI * frequency / rate
                val cosine = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val root = 2.0 * sqrt(a) * alpha
                val b0 = a * (a + 1.0 + (a - 1.0) * cosine + root)
                val b1 = -2.0 * a * (a - 1.0 + (a + 1.0) * cosine)
                val b2 = a * (a + 1.0 + (a - 1.0) * cosine - root)
                val a0 = a + 1.0 - (a - 1.0) * cosine + root
                val a1 = 2.0 * (a - 1.0 - (a + 1.0) * cosine)
                val a2 = a + 1.0 - (a - 1.0) * cosine - root
                return Biquad(b0, b1, b2, a0, a1, a2)
            }
        }
    }

    companion object {
        private const val ABSOLUTE_GATE_LUFS = -70.0
        private const val RELATIVE_GATE_LU = 10.0

        private fun gated(source: ArrayList<Double>, gateLufs: Double): ArrayList<Double> {
            val accepted = ArrayList<Double>()
            for (energy in source) {
                if (loudness(energy) >= gateLufs) accepted.add(energy)
            }
            return accepted
        }

        private fun loudness(energy: Double): Double =
            -0.691 + 10.0 * log10(max(1.0e-15, energy))

        private fun mean(values: ArrayList<Double>): Double {
            var sum = 0.0
            for (value in values) sum += value
            return if (values.isEmpty()) 0.0 else sum / values.size
        }

        private fun clamp(value: Double): Double = max(-1.0, min(1.0, value))
    }
}
