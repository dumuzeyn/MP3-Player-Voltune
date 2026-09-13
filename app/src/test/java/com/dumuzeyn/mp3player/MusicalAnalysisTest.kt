package com.dumuzeyn.mp3player

import android.media.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sin

class MusicalAnalysisTest {
    @Test fun identifiesMajorAndMinorChromaInAllKeys() {
        val major = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        val minor = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
        for (root in 0..11) for (isMinor in listOf(false, true)) {
            val profile = if (isMinor) minor else major
            val chroma = DoubleArray(12) { profile[(it - root + 12) % 12] }
            val result = checkNotNull(MusicalKeyEstimator.identify(chroma))
            assertEquals(root, result.tonic)
            assertEquals(isMinor, result.minor)
        }
    }

    @Test fun silenceNoiseAndSingleNoteDoNotInventAKey() {
        assertNull(MusicalKeyEstimator.identify(DoubleArray(12)))
        assertNull(MusicalKeyEstimator.identify(DoubleArray(12) { 1.0 }))
        assertNull(MusicalKeyEstimator.identify(DoubleArray(12) { if (it == 0) 1.0 else 0.0 }))
        assertNull(MusicalKeyEstimator.identify(DoubleArray(12) { Double.NaN }))
    }

    @Test fun fftRecognizesRealMajorAndMinorChords() {
        for (minor in listOf(false, true)) {
            val estimator = MusicalKeyEstimator(8000)
            val notes = intArrayOf(60, if (minor) 63 else 64, 67)
            repeat(8000 * 4) { index ->
                var sample = 0.0
                for ((voice, note) in notes.withIndex()) {
                    sample += (if (voice == 0) 0.38 else 0.3) * sin(2 * Math.PI * 440 * 2.0.pow((note - 69) / 12.0) * index / 8000)
                }
                estimator.addSample(sample.toFloat())
            }
            val result = checkNotNull(estimator.finish())
            assertEquals(0, result.tonic)
            assertEquals(minor, result.minor)
        }
    }

    @Test fun silenceHasNoTempo() {
        val accumulator = AudioFeatureAccumulator(8000)
        repeat(8000 * 3) { accumulator.addSample(0f) }
        val result = accumulator.finish()
        assertEquals(0.0, result[TrackAudioProfile.BPM], 0.0)
        assertEquals(0.0, result[TrackAudioProfile.TEMPO_CONFIDENCE], 0.0)
    }

    @Test fun readsSigned24BitAndUnsigned8BitAndFloatPcm() {
        val pcm24 = AudioPcmFormat(44100, 1, AudioFormat.ENCODING_PCM_24BIT_PACKED)
        assertEquals(-1f, pcm24.sample(ByteBuffer.wrap(byteArrayOf(0, 0, -128))), 0f)
        assertEquals(0.5f, pcm24.sample(ByteBuffer.wrap(byteArrayOf(0, 0, 64))), 0f)
        val pcm8 = AudioPcmFormat(8000, 1, AudioFormat.ENCODING_PCM_8BIT)
        assertEquals(0f, pcm8.sample(ByteBuffer.wrap(byteArrayOf(-128))), 0f)
        val floats = AudioPcmFormat(48000, 2, AudioFormat.ENCODING_PCM_FLOAT)
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(Float.NaN)
        buffer.rewind()
        assertEquals(0f, floats.sample(buffer), 0f)
    }
}
