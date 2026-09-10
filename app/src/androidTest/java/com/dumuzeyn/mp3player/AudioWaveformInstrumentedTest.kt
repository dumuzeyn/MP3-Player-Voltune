package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AudioWaveformInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun actualPcmProducesSilentQuietAndLoudSections() {
        val file = InstrumentedTestSupport.createTestWave(context, "waveform-levels.wav", 3)
        try {
            RandomAccessFile(file, "rw").use { output ->
                output.seek(44)
                for (frame in 0 until 24000) {
                    val gain = when (frame / 8000) { 0 -> 0; 1 -> 8000; else -> 24000 }
                    val sample = (kotlin.math.sin(2 * Math.PI * 440 * frame / 8000) * gain).toInt()
                    output.write(sample and 255)
                    output.write((sample shr 8) and 255)
                }
            }
            val before = file.readBytes()
            val waveform = AudioWaveformDecoder(context).decode(Uri.fromFile(file).toString(), 3000) { false }
            assertEquals(2048, waveform.peaks.size)
            assertEquals(0f, waveform.peakBetween(100_000, 800_000), 0f)
            assertEquals(8000 / 32768f, waveform.peakBetween(1_100_000, 1_800_000), 0.005f)
            assertEquals(24000 / 32768f, waveform.peakBetween(2_100_000, 2_800_000), 0.005f)
            assertArrayEquals(before, file.readBytes())
        } finally { file.delete() }
    }

    @Test fun cancellationDoesNotProduceFakeWaveform() {
        val file = InstrumentedTestSupport.createTestWave(context, "waveform-cancel.wav", 3)
        try {
            val result = runCatching {
                AudioWaveformDecoder(context).decode(Uri.fromFile(file).toString(), 3000) { true }
            }
            assertTrue(result.exceptionOrNull() is CancellationException)
        } finally { file.delete() }
    }

    @Test fun decodingCanBeCancelledMidStreamAndRetried() {
        val file = InstrumentedTestSupport.createTestWave(context, "waveform-retry.wav", 6)
        try {
            val decoder = AudioWaveformDecoder(context)
            val uri = Uri.fromFile(file).toString()
            var checks = 0
            val interrupted = runCatching { decoder.decode(uri, 6000) { ++checks > 2 } }
            assertTrue(interrupted.exceptionOrNull() is CancellationException)
            assertTrue(decoder.decode(uri, 6000) { false }.peaks.any { it > 0f })
        } finally { file.delete() }
    }

    @Test fun subscribersShareCachedWaveformAndClosingSuppressesCallbacks() {
        val file = InstrumentedTestSupport.createTestWave(context, "waveform-cache.wav", 3)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repository = AudioWaveformRepository(context)
        try {
            val clip = AudioEditClip(uri = Uri.fromFile(file).toString(), title = "Cache", sourceDurationMs = 3000)
            val first = AtomicReference<AudioWaveform>()
            val ready = CountDownLatch(1)
            instrumentation.runOnMainSync {
                repository.request(clip) { first.set(it.getOrThrow()); ready.countDown() }
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                var cached: AudioWaveform? = null
                repository.request(clip) { cached = it.getOrThrow() }
                assertSame(first.get(), cached)
                repository.request(clip.copy(sourceDurationMs = 3001)) { fail("Callback after close") }
                repository.close()
            }
            Thread.sleep(150)
        } finally {
            instrumentation.runOnMainSync { repository.close() }
            file.delete()
        }
    }
}
