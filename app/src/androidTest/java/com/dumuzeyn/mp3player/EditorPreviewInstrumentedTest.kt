package com.dumuzeyn.mp3player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class EditorPreviewInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: MediaController
    private lateinit var source: File
    private lateinit var preview: File
    private lateinit var tracks: List<Track>
    private var activity: MainActivityCore? = null

    @Before fun setup() {
        activity = InstrumentedTestSupport.launchForPlayback(instrumentation, context)
        controller = connect()
        main { controller.sendCustomCommand(Media3Commands.TIMER_CANCEL_COMMAND, Bundle.EMPTY) }
            .get(5, TimeUnit.SECONDS)
        main { controller.stop(); controller.clearMediaItems() }
        source = InstrumentedTestSupport.createTestWave(context, "preview-source.wav", 6)
        tracks = listOf(Track(Uri.fromFile(source).toString(), "Original one", "Tests", "Tests", "Tests", 6000),
            Track(Uri.fromFile(source).toString() + "#second", "Original two", "Tests", "Tests", "Tests", 6000))
        val done = CountDownLatch(1)
        val result = AtomicReference<Result<File>>()
        main {
            AudioEditExporter(context).export(AudioEditProject(listOf(AudioEditClip(uri = tracks.first().uri,
                title = "Preview", sourceDurationMs = 6000, endMs = 2000))), {}) {
                result.set(it); done.countDown()
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        preview = result.get().getOrThrow()
        main {
            controller.setMediaItems(tracks.map { MediaItemMapper().toMediaItem(it) }, 0, 1200)
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.shuffleModeEnabled = true
            controller.setPlaybackSpeed(1.25f)
            controller.prepare()
            controller.pause()
        }
        waitFor("Original queue not ready") { controller.playbackState == Player.STATE_READY }
    }

    @After fun cleanup() {
        if (::controller.isInitialized) {
            main { controller.sendCustomCommand(Media3Commands.TIMER_CANCEL_COMMAND, Bundle.EMPTY) }
                .get(5, TimeUnit.SECONDS)
            command("stop")
            main { controller.stop(); controller.clearMediaItems(); controller.release() }
        }
        if (::preview.isInitialized) preview.delete()
        if (::source.isInitialized) source.delete()
        InstrumentedTestSupport.finishActivity(instrumentation, activity)
    }

    @Test fun stopRestoresQueuePositionModesAndDoesNotPersistPreview() {
        start()
        waitFor("Preview did not play") { EditorPreviewSession.isPreview(controller.currentMediaItem) && controller.isPlaying }
        val persisted = PlaybackStateManager(context).load()
        assertEquals(tracks.first().uri, persisted.uri)
        assertEquals(2, persisted.queueUris.size)
        assertFalse(persisted.playing)
        assertFalse(persisted.queueUris.any { it.contains("preview:") })
        main { assertEquals(1f, controller.playbackParameters.speed, 0f) }
        command("toggle")
        waitFor("Preview not paused") { !controller.playWhenReady }
        command("seek", Bundle().apply { putLong("position", 500) })
        command("stop")
        assertRestored()
    }

    @Test fun naturalEndRestoresPausedMusicWithoutStartingIt() {
        start()
        waitFor("Preview not started") { EditorPreviewSession.isPreview(controller.currentMediaItem) }
        waitFor("Original queue not restored on end") { !EditorPreviewSession.isPreview(controller.currentMediaItem) }
        assertRestored()
    }

    @Test fun ordinaryPlaybackCommandLeavesPreviewAndAppliesToMusic() {
        start()
        waitFor("Preview not started") { EditorPreviewSession.isPreview(controller.currentMediaItem) }
        main { controller.pause() }
        waitFor("Ordinary pause did not restore music") { !EditorPreviewSession.isPreview(controller.currentMediaItem) }
        assertRestored()
    }

    @Test fun disconnectRestoresPlayingMusicForNextController() {
        main { controller.play() }
        waitFor("Music not playing") { controller.isPlaying }
        start()
        waitFor("Preview not started") { EditorPreviewSession.isPreview(controller.currentMediaItem) }
        main { controller.release() }
        controller = connect()
        waitFor("Disconnect left preview running") { !EditorPreviewSession.isPreview(controller.currentMediaItem) && controller.isPlaying }
        main { assertEquals(2, controller.mediaItemCount); assertEquals(1.25f, controller.playbackParameters.speed, 0f) }
    }

    @Test fun invalidPathCannotReplaceMusicSession() {
        val result = command("start", Bundle().apply { putString("path", source.absolutePath); putString("token", "bad") })
        assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        assertRestored()
    }

    @Test fun invalidEncodedAudioReturnsToMusicInsteadOfSkippingQueue() {
        val bad = File.createTempFile("voltune-edit-", ".m4a", context.cacheDir)
        try {
            bad.writeBytes(byteArrayOf(1, 2, 3))
            val result = command("start", Bundle().apply {
                putString("path", bad.absolutePath)
                putString("token", UUID.randomUUID().toString())
            })
            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            waitFor("Failed preview not restored") { !EditorPreviewSession.isPreview(controller.currentMediaItem) }
            assertRestored()
            assertTrue(command("state").extras.getBoolean("error"))
        } finally { bad.delete() }
    }

    @Test fun sleepTimerRestoresQueueButDoesNotResumeMusic() {
        main { controller.play() }
        waitFor("Music not playing") { controller.isPlaying }
        start()
        main {
            controller.sendCustomCommand(Media3Commands.TIMER_START_COMMAND, Bundle().apply {
                putLong(Media3Commands.ARG_TIMER_MS, 1000)
            })
        }
        waitFor("Timer did not stop preview") { !EditorPreviewSession.isPreview(controller.currentMediaItem) && !controller.playWhenReady }
        main { assertEquals(2, controller.mediaItemCount); assertEquals(Player.REPEAT_MODE_ALL, controller.repeatMode) }
    }

    private fun start() {
        val result = command("start", Bundle().apply {
            putString("path", preview.absolutePath)
            putString("token", UUID.randomUUID().toString())
        })
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    }

    private fun assertRestored() {
        waitFor("Restored track not ready") { !EditorPreviewSession.isPreview(controller.currentMediaItem) &&
            controller.playbackState == Player.STATE_READY }
        main {
            assertEquals(2, controller.mediaItemCount)
            assertEquals(tracks.first().uri, controller.currentMediaItem?.localConfiguration?.uri.toString())
            assertEquals(1200, controller.currentPosition)
            assertFalse(controller.playWhenReady)
            assertEquals(Player.REPEAT_MODE_ALL, controller.repeatMode)
            assertTrue(controller.shuffleModeEnabled)
            assertEquals(1.25f, controller.playbackParameters.speed, 0f)
        }
    }

    private fun command(action: String, args: Bundle = Bundle()): SessionResult {
        args.putString("action", action)
        return main { controller.sendCustomCommand(Media3Commands.EDITOR_PREVIEW_COMMAND, args) }.get(10, TimeUnit.SECONDS)
    }

    private fun connect() = MediaController.Builder(context, SessionToken(context,
        ComponentName(context, Media3PlayerService::class.java))).setApplicationLooper(Looper.getMainLooper())
        .buildAsync().get(15, TimeUnit.SECONDS)

    private fun waitFor(message: String, condition: () -> Boolean) =
        InstrumentedTestSupport.waitFor(message, 15000) { main(condition) }

    private fun <T> main(action: () -> T): T {
        val task = FutureTask<T> { action() }
        instrumentation.runOnMainSync(task)
        return task.get()
    }
}
