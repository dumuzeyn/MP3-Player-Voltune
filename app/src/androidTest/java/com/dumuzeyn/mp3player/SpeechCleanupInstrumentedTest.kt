package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random
import java.util.concurrent.CancellationException

@RunWith(AndroidJUnit4::class)
class SpeechCleanupInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun neuralCleanupReducesStationaryNoiseAndKeepsExactSelectedDuration() {
        val source = File(context.cacheDir, "speech-noise-source.wav")
        val output = File(context.cacheDir, "speech-noise-clean.wav")
        val random = Random(17)
        try {
            PcmWaveWriter(source, 48000, 2).use { writer ->
                repeat(48000 * 4) { frame ->
                    writer.sample(if (frame < 48000) 0f else random.nextFloat() * .08f - .04f)
                    writer.sample(0f)
                }
            }
            val original = source.readBytes()
            val clip = AudioEditClip(uri = Uri.fromFile(source).toString(), title = "Noise",
                sourceDurationMs = 4000, startMs = 1003, endMs = 3997)
            SpeechCleanupProcessor(context).process(clip, output, { false }) { }
            var frames = 0
            var energy = 0.0
            AudioPcmDecoder(context).decode(Uri.fromFile(output).toString(), 0, 2994, { false }) { format, pcm, _ ->
                assertEquals(48000, format.sampleRate)
                assertEquals(2, format.channels)
                while (pcm.remaining() >= format.frameBytes) {
                    val sample = format.sample(pcm)
                    if (frames > 48000) energy += sample * sample
                    assertEquals(0f, format.sample(pcm), 1f / 32768)
                    frames++
                }
            }
            assertEquals(2994 * 48, frames)
            assertTrue("Noise should be reduced by the neural model", energy / (frames - 48000) < .0002)
            assertArrayEquals(original, source.readBytes())
        } finally { source.delete(); output.delete() }
    }

    @Test fun cancellationRemovesPartialOutputAndCanBeRetried() {
        val source = InstrumentedTestSupport.createTestWave(context, "speech-cancel-source.wav", 3)
        val output = File(context.cacheDir, "speech-cancel-output.wav")
        val clip = AudioEditClip(uri = Uri.fromFile(source).toString(), title = "Speech", sourceDurationMs = 3000)
        try {
            var cancel = false
            val result = runCatching {
                SpeechCleanupProcessor(context).process(clip, output, { cancel }) { cancel = true }
            }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertFalse(output.exists())
            SpeechCleanupProcessor(context).process(clip, output, { false }) { }
            assertEquals(44 + 48000 * 3 * 2L, output.length())
        } finally { source.delete(); output.delete() }
    }

    @Test fun silenceStaysSilentAndNativeStateRejectsWrongFrameSize() {
        SpeechDenoiser().use { denoiser ->
            repeat(4) {
                val silence = FloatArray(480)
                denoiser.frame(silence)
                assertTrue(silence.all { it == 0f })
            }
            assertTrue(runCatching { denoiser.frame(FloatArray(479)) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
