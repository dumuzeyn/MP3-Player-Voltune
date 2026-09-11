package com.dumuzeyn.mp3player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AudioFormatsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val files = ArrayList<File>()
    private var controller: MediaController? = null
    private var exporter: AudioEditExporter? = null

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            exporter?.close()
            controller?.apply { stop(); clearMediaItems(); release() }
        }
        files.forEach { it.delete() }
    }

    @Test fun supportedFormatsPlayDecodeAndExportToAac() {
        val player = MediaController.Builder(context, SessionToken(context,
            ComponentName(context, Media3PlayerService::class.java)))
            .setApplicationLooper(Looper.getMainLooper()).buildAsync().get(15, TimeUnit.SECONDS)
        controller = player
        for (extension in listOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac")) {
            val source = File(context.cacheDir, "format-test.$extension").also(files::add)
            instrumentation.context.assets.open("audio-formats/tone.$extension").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            val original = source.readBytes()
            val uri = Uri.fromFile(source).toString()
            assertTrue(extension, AudioImportController.hasAudioExtension(source.name))
            instrumentation.runOnMainSync {
                player.stop()
                player.setMediaItem(MediaItem.fromUri(uri))
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.prepare()
                player.play()
            }
            InstrumentedTestSupport.waitFor("$extension did not play", 15000) {
                var ready = false
                instrumentation.runOnMainSync {
                    assertNull("$extension playback error", player.playerError)
                    ready = player.isPlaying && player.currentPosition >= 100
                }
                ready
            }
            instrumentation.runOnMainSync { player.pause() }
            val waveform = AudioWaveformDecoder(context).decode(uri, 2000) { false }
            assertTrue("$extension waveform silent", waveform.peaks.any { it > .01f })
            val clip = AudioEditClip(uri = uri, title = extension, sourceDurationMs = 2000,
                startMs = 200, endMs = 1200)
            val completed = CountDownLatch(1)
            val result = AtomicReference<Result<File>>()
            instrumentation.runOnMainSync {
                exporter = AudioEditExporter(context).apply {
                    export(AudioEditProject(listOf(clip)), {}) { result.set(it); completed.countDown() }
                }
            }
            assertTrue("$extension export timeout", completed.await(30, TimeUnit.SECONDS))
            val output = result.get().getOrThrow().also(files::add)
            assertTrue("$extension export silent", ExportAudioProbe.rms(output) > 10)
            val extractor = android.media.MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val duration = extractor.getTrackFormat(0).getLong(android.media.MediaFormat.KEY_DURATION) / 1000
                assertTrue("$extension trim duration=$duration", duration in 950..1100)
            } finally { extractor.release() }
            assertArrayEquals("$extension source changed", original, source.readBytes())
            android.util.Log.i("VoltuneFormatsTest", "$extension: playback, waveform and AAC export passed")
        }
    }

    @Test fun rawAacIndexingPreservesCompressedFramesAndHonorsCancellation() {
        val source = File(context.cacheDir, "index-test.aac").also(files::add)
        instrumentation.context.assets.open("audio-formats/tone.aac").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val clip = AudioEditClip(uri = Uri.fromFile(source).toString(), title = "AAC",
            sourceDurationMs = 2000, startMs = 200)
        val project = AudioEditProject(listOf(clip))
        val prepared = AudioEditSeekableSource.prepare(context, project, files)
        assertNotEquals(clip.uri, prepared.clips.single().uri)
        assertArrayEquals(encodedFrames(clip.uri), encodedFrames(prepared.clips.single().uri))
        try {
            Thread.currentThread().interrupt()
            val cancelled = runCatching { AudioEditSeekableSource.prepare(context, project, files) }
            assertTrue(cancelled.exceptionOrNull() is java.util.concurrent.CancellationException)
        } finally { Thread.interrupted() }
    }

    private fun encodedFrames(uri: String): ByteArray {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            extractor.selectTrack(0)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0)
                buffer.limit(size)
                digest.update(buffer)
                extractor.advance()
            }
            return digest.digest()
        } finally { extractor.release() }
    }
}
