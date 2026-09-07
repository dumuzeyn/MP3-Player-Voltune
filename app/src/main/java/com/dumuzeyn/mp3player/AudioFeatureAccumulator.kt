package com.dumuzeyn.mp3player

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln1p
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Streaming audio statistics with bounded memory and a small, decimated spectrum. */
internal class AudioFeatureAccumulator(sampleRate: Int) {
    private val sampleRate = max(8_000, sampleRate)
    private val envelopeBlockSize = max(1, this.sampleRate / 50)
    private val frame = FloatArray(FRAME_SIZE)
    private val envelope = ArrayList<Double>()
    private val blockLevels = ArrayList<Double>()
    private val timbre = DoubleArray(TIMBRE_COEFFICIENTS)
    private var framePosition = 0
    private var completedFrames = 0
    private var sampleCount = 0L
    private var squareSum = 0.0
    private var absoluteSum = 0.0
    private var peak = 0.0
    private var previous = 0f
    private var hasPrevious = false
    private var zeroCrossings = 0L
    private var blockSamples = 0
    private var blockSquareSum = 0.0
    private var centroidSum = 0.0
    private var bandwidthSum = 0.0
    private var rolloffSum = 0.0
    private var bassRatioSum = 0.0
    private var trebleRatioSum = 0.0
    private var contrastSum = 0.0
    private var spectralFrames = 0

    fun beginSegment() {
        hasPrevious = false
        finishEnvelopeBlock()
    }

    fun addPcm(source: ByteBuffer, encoding: Int, channels: Int) {
        val pcm = source.order(ByteOrder.LITTLE_ENDIAN)
        val channelCount = max(1, channels)
        while (hasFrame(pcm, encoding, channelCount)) {
            var mono = 0.0
            for (channel in 0 until channelCount) {
                mono += readSample(pcm, encoding)
            }
            addSample((mono / channelCount).toFloat())
        }
    }

    fun addSample(source: Float) {
        val sample = source.coerceIn(-1f, 1f)
        val absolute = abs(sample.toDouble())
        sampleCount++
        squareSum += sample * sample
        absoluteSum += absolute
        peak = max(peak, absolute)
        if (hasPrevious && (
                previous < 0f && sample >= 0f ||
                    previous >= 0f && sample < 0f
                )
        ) {
            zeroCrossings++
        }
        previous = sample
        hasPrevious = true
        blockSquareSum += sample * sample
        blockSamples++
        if (blockSamples >= envelopeBlockSize) finishEnvelopeBlock()
        frame[framePosition++] = sample
        if (framePosition == FRAME_SIZE) {
            if (completedFrames++ % SPECTRAL_FRAME_STEP == 0) analyzeSpectrum()
            framePosition = 0
        }
    }

    fun finish(): DoubleArray {
        finishEnvelopeBlock()
        if (sampleCount < sampleRate / 2L) return DoubleArray(0)
        val result = DoubleArray(TrackAudioProfile.FEATURE_COUNT)
        val rms = sqrt(squareSum / sampleCount)
        result[TrackAudioProfile.BPM] = estimateBpm()
        result[TrackAudioProfile.ENERGY] = absoluteSum / sampleCount
        result[TrackAudioProfile.LOUDNESS] = decibels(rms)
        result[TrackAudioProfile.DYNAMIC_RANGE] = dynamicRange()
        val frames = max(1, spectralFrames)
        result[TrackAudioProfile.CENTROID] = centroidSum / frames
        result[TrackAudioProfile.BANDWIDTH] = bandwidthSum / frames
        result[TrackAudioProfile.ROLLOFF] = rolloffSum / frames
        result[TrackAudioProfile.ZERO_CROSSING] = zeroCrossings.toDouble() / sampleCount
        result[TrackAudioProfile.BASS] = bassRatioSum / frames
        result[TrackAudioProfile.TREBLE] = trebleRatioSum / frames
        result[TrackAudioProfile.RHYTHM] = rhythmStrength()
        result[TrackAudioProfile.CONTRAST] = contrastSum / frames
        for (index in 0 until TIMBRE_COEFFICIENTS) {
            result[TrackAudioProfile.TIMBRE_START + index] = timbre[index] / frames
        }
        return result
    }

    private fun finishEnvelopeBlock() {
        if (blockSamples == 0) return
        val rms = sqrt(blockSquareSum / blockSamples)
        envelope.add(rms)
        blockLevels.add(decibels(rms))
        blockSquareSum = 0.0
        blockSamples = 0
    }

    private fun analyzeSpectrum() {
        val powers = DoubleArray(SPECTRAL_BINS)
        var total = 0.0
        for (bin in 0 until SPECTRAL_BINS) {
            var real = 0.0
            var imaginary = 0.0
            for (sample in 0 until FRAME_SIZE) {
                val value = frame[sample] * HANN[sample]
                real += value * COSINE[bin][sample]
                imaginary -= value * SINE[bin][sample]
            }
            powers[bin] = real * real + imaginary * imaginary
            total += powers[bin]
        }
        if (total <= 1.0e-12) {
            spectralFrames++
            return
        }
        var weighted = 0.0
        var bass = 0.0
        var treble = 0.0
        var cumulative = 0.0
        var rolloffBin = powers.lastIndex
        for (bin in powers.indices) {
            val frequency = frequency(bin)
            weighted += frequency * powers[bin]
            if (frequency <= 250.0) bass += powers[bin]
            if (frequency >= 4_000.0) treble += powers[bin]
            cumulative += powers[bin]
            if (cumulative >= total * 0.85 && rolloffBin == powers.lastIndex) {
                rolloffBin = bin
            }
        }
        val centroid = weighted / total
        var spread = 0.0
        for (bin in powers.indices) {
            val delta = frequency(bin) - centroid
            spread += delta * delta * powers[bin]
        }
        val nyquist = sampleRate / 2.0
        centroidSum += centroid / nyquist
        bandwidthSum += sqrt(spread / total) / nyquist
        rolloffSum += frequency(rolloffBin) / nyquist
        bassRatioSum += bass / total
        trebleRatioSum += treble / total
        contrastSum += spectralContrast(powers)
        addTimbre(powers)
        spectralFrames++
    }

    private fun addTimbre(powers: DoubleArray) {
        val bands = DoubleArray(TIMBRE_BANDS)
        for (bin in powers.indices) {
            val band = min(TIMBRE_BANDS - 1, bin * TIMBRE_BANDS / powers.size)
            bands[band] += powers[bin]
        }
        for (coefficient in 0 until TIMBRE_COEFFICIENTS) {
            var value = 0.0
            for (band in bands.indices) {
                value += ln1p(bands[band]) * cos(
                    Math.PI * coefficient * (band + 0.5) / bands.size,
                )
            }
            timbre[coefficient] += value / bands.size
        }
    }

    private fun estimateBpm(): Double {
        if (envelope.size < 100) return 0.0
        val minimumLag = 50 * 60 / 200
        val maximumLag = min(envelope.size / 2, 50 * 60 / 60)
        var mean = 0.0
        for (value in envelope) mean += value
        mean /= envelope.size
        var best = Double.NEGATIVE_INFINITY
        var bestLag = minimumLag
        for (lag in minimumLag..maximumLag) {
            var correlation = 0.0
            for (index in lag until envelope.size) {
                correlation += (envelope[index] - mean) * (envelope[index - lag] - mean)
            }
            if (correlation > best) {
                best = correlation
                bestLag = lag
            }
        }
        return 3_000.0 / bestLag
    }

    private fun rhythmStrength(): Double {
        if (envelope.size < 3) return 0.0
        var positiveChanges = 0.0
        var level = 0.0
        for (index in 1 until envelope.size) {
            positiveChanges += max(0.0, envelope[index] - envelope[index - 1])
            level += envelope[index]
        }
        return positiveChanges / max(1.0e-9, level)
    }

    private fun dynamicRange(): Double {
        if (blockLevels.isEmpty()) return 0.0
        val sorted = DoubleArray(blockLevels.size) { blockLevels[it] }
        Arrays.sort(sorted)
        return sorted[((sorted.size - 1) * 0.90).toInt()] -
            sorted[((sorted.size - 1) * 0.10).toInt()]
    }

    private fun frequency(bin: Int): Double = (bin + 1.0) * sampleRate / FRAME_SIZE

    companion object {
        private const val FRAME_SIZE = 256
        private const val SPECTRAL_BINS = 48
        private const val TIMBRE_BANDS = 16
        private const val TIMBRE_COEFFICIENTS = 6
        private const val SPECTRAL_FRAME_STEP = 8
        private val HANN = DoubleArray(FRAME_SIZE)
        private val COSINE = Array(SPECTRAL_BINS) { DoubleArray(FRAME_SIZE) }
        private val SINE = Array(SPECTRAL_BINS) { DoubleArray(FRAME_SIZE) }

        init {
            for (sample in 0 until FRAME_SIZE) {
                HANN[sample] = 0.5 - 0.5 * cos(
                    2.0 * Math.PI * sample / (FRAME_SIZE - 1),
                )
                for (bin in 0 until SPECTRAL_BINS) {
                    val angle = 2.0 * Math.PI * (bin + 1) * sample / FRAME_SIZE
                    COSINE[bin][sample] = cos(angle)
                    SINE[bin][sample] = sin(angle)
                }
            }
        }

        private fun spectralContrast(powers: DoubleArray): Double {
            val sorted = powers.clone()
            Arrays.sort(sorted)
            val quarter = max(1, sorted.size / 4)
            var low = 0.0
            var high = 0.0
            for (index in 0 until quarter) {
                low += sorted[index]
                high += sorted[sorted.lastIndex - index]
            }
            return ln1p(high / quarter) - ln1p(low / quarter)
        }

        private fun decibels(value: Double): Double = 20.0 * log10(max(1.0e-9, value))

        private fun hasFrame(source: ByteBuffer, encoding: Int, channels: Int): Boolean =
            source.remaining() >= bytesPerSample(encoding) * channels

        private fun bytesPerSample(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }

        private fun readSample(source: ByteBuffer, encoding: Int): Float = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> source.float
            AudioFormat.ENCODING_PCM_32BIT -> source.int / 2_147_483_648f
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val value = (source.get().toInt() and 0xff) or
                    ((source.get().toInt() and 0xff) shl 8) or
                    (source.get().toInt() shl 16)
                value / 8_388_608f
            }
            AudioFormat.ENCODING_PCM_8BIT ->
                ((source.get().toInt() and 0xff) - 128) / 128f
            else -> source.short / 32_768f
        }
    }
}
