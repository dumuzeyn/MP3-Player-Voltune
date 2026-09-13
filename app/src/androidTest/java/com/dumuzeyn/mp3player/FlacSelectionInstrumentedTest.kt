package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CancellationException

@RunWith(AndroidJUnit4::class)
class FlacSelectionInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = ArrayList<File>()

    @After fun cleanup() { files.forEach { it.delete() } }

    @Test fun preparationPreservesSelectedSamplesAndTimelineWithoutChangingSource() {
        val source = File(context.cacheDir, "precise-selection.flac").also(files::add)
        InstrumentationRegistry.getInstrumentation().context.assets.open("audio-formats/tone.flac").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val original = source.readBytes()
        val clip = AudioEditClip(uri = Uri.fromFile(source).toString(), title = "FLAC",
            sourceDurationMs = 2000, startMs = 201, endMs = 1199, lane = 2, offsetMs = 345, gain = .4f)
        val expected = ByteArrayOutputStream()
        AudioPcmDecoder(context).decode(clip.uri, clip.startMs, clip.endMs, { false }) { _, pcm, _ ->
            val bytes = ByteArray(pcm.remaining())
            pcm.get(bytes)
            expected.write(bytes)
        }
        val input = AudioEditProject(listOf(clip))
        val prepared = AudioEditFlacSource.prepare(context, input, files)
        val selected = prepared.clips.single()
        assertEquals(input.durationMs, prepared.durationMs)
        assertEquals(clip.id, selected.id)
        assertEquals(clip.lane, selected.lane)
        assertEquals(clip.offsetMs, selected.offsetMs)
        assertEquals(clip.gain, selected.gain, 0f)
        assertEquals(0L, selected.startMs)
        assertEquals(998L, selected.endMs)
        val wave = File(checkNotNull(Uri.parse(selected.uri).path)).readBytes()
        assertArrayEquals(expected.toByteArray(), wave.copyOfRange(44, wave.size))
        assertEquals(998 * 48 * 2 * 2, wave.size - 44)
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun cancellationDoesNotCreateTemporaryFiles() {
        val input = AudioEditProject(listOf(AudioEditClip(uri = "content://test/song.flac",
            title = "FLAC", sourceDurationMs = 2000, endMs = 1000)))
        try {
            Thread.currentThread().interrupt()
            val result = runCatching { AudioEditFlacSource.prepare(context, input, files) }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue(files.isEmpty())
        } finally { Thread.interrupted() }
    }
}
