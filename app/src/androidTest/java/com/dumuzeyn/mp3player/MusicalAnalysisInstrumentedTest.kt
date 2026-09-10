package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MusicalAnalysisInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun pcmSelectionHasExactBoundsAndDoesNotIncludeOutsideAudio() {
        val file = InstrumentedTestSupport.createTestWave(context, "pcm-boundaries.wav", 3)
        try {
            RandomAccessFile(file, "rw").use { out ->
                out.seek(44)
                repeat(24000) { frame ->
                    val sample = if (frame in 8000 until 16000) 0 else 16000
                    out.write(sample and 255)
                    out.write(sample shr 8)
                }
            }
            var frames = 0
            AudioPcmDecoder(context).decode(Uri.fromFile(file).toString(), 1000, 2000, { false }) { format, pcm, time ->
                assertTrue(time in 1000000 until 2000000)
                while (pcm.remaining() >= format.frameBytes) {
                    repeat(format.channels) { assertEquals(0f, format.sample(pcm), 0f) }
                    frames++
                }
            }
            assertEquals(8000, frames)
        } finally { file.delete() }
    }

    @Test fun realDecoderAndFftIdentifyChordAndRejectSilentSelection() {
        val file = InstrumentedTestSupport.createTestWave(context, "key-chord.wav", 6)
        try {
            RandomAccessFile(file, "rw").use { out ->
                out.seek(44)
                repeat(48000) { frame ->
                    val sample = if (frame >= 32000) 0 else (listOf(60, 64, 67).mapIndexed { i, note ->
                        sin(2 * PI * (440 * 2.0.pow((note - 69) / 12.0)) * frame / 8000) *
                            (if (i == 0) .38 else .3)
                    }.sum() * 32767).toInt()
                    out.write(sample and 255)
                    out.write((sample shr 8) and 255)
                }
            }
            val clip = AudioEditClip(uri = Uri.fromFile(file).toString(), title = "Chord", sourceDurationMs = 6000)
            val extractor = AudioFeatureExtractor(context)
            val chord = extractor.musical(clip.copy(endMs = 4000)) { false }
            assertEquals(0, chord.key?.tonic)
            assertEquals(false, chord.key?.minor)
            val silent = extractor.musical(clip.copy(startMs = 4000)) { false }
            assertNull(silent.key)
            assertEquals(0.0, silent.bpm, 0.0)
        } finally { file.delete() }
    }
}
