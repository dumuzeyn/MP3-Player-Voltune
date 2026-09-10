package com.dumuzeyn.mp3player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AudioEditorExportInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val files = ArrayList<File>()
    private val engines = ArrayList<AudioEditExporter>()

    @After fun cleanup() {
        instrumentation.runOnMainSync { engines.forEach { it.close() } }
        files.forEach { it.delete() }
    }

    @Test fun trimSplitAndConcatenateProduceRealAudioWithoutChangingSource() {
        val wave = source("trim.wav")
        val bytes = wave.readBytes()
        val clip = AudioEditClip(uri = Uri.fromFile(wave).toString(), title = "Trim", sourceDurationMs = 6000,
            startMs = 1000, endMs = 5000)
        val project = AudioEditProject(listOf(clip)).split(clip.id, 3000).concatenate()
        verifyAudio(export(project), 4000)
        assertArrayEquals(bytes, wave.readBytes())
    }

    @Test fun removeRangeExportsShorterAudio() {
        val clip = AudioEditClip(uri = Uri.fromFile(source("remove.wav")).toString(), title = "Remove",
            sourceDurationMs = 6000)
        verifyAudio(export(AudioEditProject(listOf(clip)).removeRange(clip.id, 2000, 4000)), 4000)
    }

    @Test fun mixingLanesIncludesDelayedAudioAndVolumeProcessing() {
        val uri = Uri.fromFile(source("mix.wav")).toString()
        val first = AudioEditClip(uri = uri, title = "First", sourceDurationMs = 6000, endMs = 3000, gain = 0.4f)
        val second = AudioEditClip(uri = uri, title = "Second", sourceDurationMs = 6000,
            endMs = 3000, lane = 1, offsetMs = 2000, gain = 0.4f)
        verifyAudio(export(AudioEditProject(listOf(first, second))), 5000)
    }

    @Test fun clipVolumeChangesDecodedSamples() {
        val clip = AudioEditClip(uri = Uri.fromFile(source("volume.wav")).toString(), title = "Volume",
            sourceDurationMs = 6000, endMs = 2000)
        val loud = ExportAudioProbe.rms(export(AudioEditProject(listOf(clip))))
        val quiet = ExportAudioProbe.rms(export(AudioEditProject(listOf(clip.copy(gain = 0.25f)))))
        assertTrue("Audio is silent", loud > 100)
        assertTrue("Gain ratio ${quiet / loud}", quiet / loud in 0.20..0.30)
    }

    private fun source(name: String): File = InstrumentedTestSupport.createTestWave(context, name, 6)
        .also(files::add)

    private fun export(project: AudioEditProject): File {
        val completed = CountDownLatch(1)
        val output = AtomicReference<Result<File>>()
        instrumentation.runOnMainSync {
            val engine = AudioEditExporter(context).also(engines::add)
            engine.export(project, {}) { result -> output.set(result); completed.countDown() }
        }
        assertTrue("Export timed out", completed.await(45, TimeUnit.SECONDS))
        return output.get().getOrThrow().also(files::add)
    }

    private fun verifyAudio(file: File, expectedMs: Long) {
        assertTrue("Export is empty", file.length() > 1000)
        assertTrue("Exported audio is silent", ExportAudioProbe.rms(file) > 10)
        val waveform = AudioWaveformDecoder(context).decode(Uri.fromFile(file).toString(), expectedMs) { false }
        assertTrue("Exported AAC waveform is empty", waveform.peaks.any { it > 0.001f })
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
            val durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
            assertTrue("Duration $durationMs differs from $expectedMs", kotlin.math.abs(durationMs - expectedMs) < 300)
            extractor.selectTrack(0)
            val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
            var count = 0
            while (extractor.readSampleData(buffer, 0) >= 0) {
                count++
                extractor.advance()
            }
            assertTrue("No encoded audio frames", count > 10)
        } finally { extractor.release() }
    }
}
