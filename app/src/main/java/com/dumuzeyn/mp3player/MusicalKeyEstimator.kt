package com.dumuzeyn.mp3player

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class MusicalKey(val tonic: Int, val minor: Boolean, val correlation: Double) {
    fun label(russian: Boolean): String = NOTES[tonic] + if (russian) {
        if (minor) " минор" else " мажор"
    } else if (minor) " minor" else " major"
    companion object { private val NOTES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B") }
}

/** FFT chroma with Krumhansl-Kessler profile correlation; ambiguous audio stays unknown. */
internal class MusicalKeyEstimator(private val sampleRate: Int) {
    private val fft = FloatFFT_1D(SIZE.toLong())
    private val frame = FloatArray(SIZE)
    private val spectrum = FloatArray(SIZE)
    private val chroma = DoubleArray(12)
    private var used = 0
    private var frames = 0
    fun beginSegment() { used = 0 }
    fun addSample(sample: Float) {
        frame[used++] = if (sample.isFinite()) sample else 0f
        if (used == SIZE) { analyze(); used = 0 }
    }

    private fun analyze() {
        for (index in frame.indices) spectrum[index] = frame[index] * WINDOW[index]
        fft.realForward(spectrum)
        val powers = DoubleArray(SIZE / 2)
        for (bin in 1 until powers.size) {
            powers[bin] = spectrum[bin * 2].toDouble() * spectrum[bin * 2] +
                spectrum[bin * 2 + 1].toDouble() * spectrum[bin * 2 + 1]
        }
        val maximum = powers.maxOrNull() ?: return
        if (maximum < 1e-6) return
        val current = DoubleArray(12)
        for (bin in 2 until powers.size - 1) {
            val frequency = bin.toDouble() * sampleRate / SIZE
            if (frequency !in 55.0..4000.0 || powers[bin] < maximum * 0.01 ||
                powers[bin] < powers[bin - 1] || powers[bin] < powers[bin + 1]) continue
            val left = ln(powers[bin - 1].coerceAtLeast(1e-20))
            val center = ln(powers[bin].coerceAtLeast(1e-20))
            val right = ln(powers[bin + 1].coerceAtLeast(1e-20))
            val denominator = left - 2 * center + right
            val offset = if (abs(denominator) > 1e-12) (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5) else 0.0
            val midi = 69 + 12 * ln((bin + offset) * sampleRate / SIZE / 440) / ln(2.0)
            val note = midi.roundToInt()
            if (abs(midi - note) < 0.4) current[note % 12] += sqrt(powers[bin])
        }
        val total = current.sum()
        if (total > 0) {
            for (index in chroma.indices) chroma[index] += current[index] / total
            frames++
        }
    }

    fun finish(): MusicalKey? = if (frames < 2) null else identify(chroma)

    companion object {
        private const val SIZE = 8192
        private val WINDOW = FloatArray(SIZE) { (0.5 - 0.5 * cos(2 * Math.PI * it / (SIZE - 1))).toFloat() }
        private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
        fun identify(chroma: DoubleArray): MusicalKey? {
            require(chroma.size == 12)
            if (chroma.any { !it.isFinite() || it < 0 }) return null
            val maximum = chroma.maxOrNull() ?: 0.0
            if (maximum <= 0 || chroma.count { it > maximum * 0.08 } < 3) return null
            val mean = chroma.average()
            val variance = chroma.sumOf { (it - mean) * (it - mean) }
            if (variance < 1e-12) return null
            val candidates = ArrayList<MusicalKey>()
            for (minor in listOf(false, true)) {
                val profile = if (minor) MINOR else MAJOR
                val average = profile.average()
                val norm = sqrt(variance * profile.sumOf { (it - average) * (it - average) })
                for (tonic in 0..11) {
                    val score = (0..11).sumOf { (chroma[(it + tonic) % 12] - mean) * (profile[it] - average) } / norm
                    candidates.add(MusicalKey(tonic, minor, score))
                }
            }
            candidates.sortByDescending { it.correlation }
            return candidates.first().takeIf { it.correlation >= 0.55 && it.correlation - candidates[1].correlation >= 0.04 }
        }
    }
}
