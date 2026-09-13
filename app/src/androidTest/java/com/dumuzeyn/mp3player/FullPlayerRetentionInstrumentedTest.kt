package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FullPlayerRetentionInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: MainActivityCore? = null
    private val files = ArrayList<File>()

    @After fun cleanup() {
        activity?.let { host ->
            instrumentation.runOnMainSync { host.playbackController.clearQueue() }
            InstrumentedTestSupport.finishActivity(instrumentation, host)
        }
        PlaybackStateManager(context).clear()
        files.forEach { it.delete() }
    }

    @Test fun expiredPauseClosesBothPlayerAndQueuePagesAfterScreenOffLifecycle() {
        val host = launch()
        for (page in listOf(FullPlayerPageOrder.PLAYER, FullPlayerPageOrder.QUEUE)) {
            prepareQueue(host, false)
            openPage(host, page)
            cycleLifecycle(host, 8 * 60 * 60 * 1000L)
            await("Expired queue is still connected") { host.playbackSnapshot().currentMediaId.isEmpty() }
            instrumentation.runOnMainSync {
                assertFalse(descendants(host.overlayHost).any { it is FullPlayerSheet })
                assertTrue(host.playbackUiState.queue.isEmpty())
                assertTrue(host.miniPlayer == null || host.miniPlayer?.visibility == View.GONE)
                assertFalse(host.playbackSnapshot().playWhenReady)
            }
        }
    }

    @Test fun unexpiredPauseKeepsPageTrackAndPosition() {
        val host = launch()
        prepareQueue(host, false)
        openPage(host, FullPlayerPageOrder.QUEUE)
        val before = host.playbackSnapshot().currentMediaId
        var pausedPosition = 0L
        instrumentation.runOnMainSync { pausedPosition = host.playbackController.currentPosition() }
        assertTrue("Initial seek did not reach the requested position", pausedPosition in 1134L..1334L)
        cycleLifecycle(host, 60 * 60 * 1000L)
        instrumentation.runOnMainSync {
            assertTrue(descendants(host.overlayHost).any { it is FullPlayerSheet })
            assertEquals(FullPlayerPageOrder.QUEUE, descendants(host.overlayHost).filterIsInstance<ViewPager2>().single().currentItem)
            assertEquals(before, host.playbackSnapshot().currentMediaId)
            assertEquals(pausedPosition, host.playbackController.currentPosition())
            assertEquals(2, host.playbackUiState.queue.size)
        }
    }

    @Test fun activelyPlayingSessionDoesNotExpire() {
        val host = launch()
        prepareQueue(host, true)
        openPage(host, FullPlayerPageOrder.PLAYER)
        val before = host.playbackSnapshot().currentMediaId
        cycleLifecycle(host, 8 * 60 * 60 * 1000L)
        instrumentation.runOnMainSync {
            assertTrue(descendants(host.overlayHost).any { it is FullPlayerSheet })
            assertEquals(before, host.playbackSnapshot().currentMediaId)
            assertTrue(host.playbackSnapshot().playWhenReady)
        }
    }

    private fun prepareQueue(host: MainActivityCore, playing: Boolean) {
        instrumentation.runOnMainSync {
            host.appearanceState.resumeWindowMinutes = 120
            host.playbackController.submitQueue(host.libraryState.tracks.take(2), 1, 1234, 1, playing)
        }
        await("Queue did not become ready") {
            host.playbackSnapshot().phase == PlaybackPhase.READY && host.playbackUiState.queue.size == 2 &&
                host.playbackSnapshot().currentIndex == 1 && host.playbackSnapshot().playWhenReady == playing
        }
        await("Queue was not persisted") { PlaybackStateManager(context).load().queueUris.size == 2 }
    }

    private fun openPage(host: MainActivityCore, page: Int) {
        instrumentation.runOnMainSync { host.playerUiController.openFullPlayer() }
        await("Full player did not open") { descendants(host.overlayHost).any { it is ViewPager2 } }
        instrumentation.runOnMainSync {
            descendants(host.overlayHost).filterIsInstance<ViewPager2>().single().setCurrentItem(page, false)
        }
    }

    private fun cycleLifecycle(host: MainActivityCore, inactiveMs: Long) {
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(host)
            instrumentation.callActivityOnStop(host)
            context.getSharedPreferences(PlaybackStateManager.PREFS, 0).edit()
                .putLong("inactiveSince", System.currentTimeMillis() - inactiveMs).commit()
            instrumentation.callActivityOnStart(host)
            instrumentation.callActivityOnResume(host)
        }
    }

    private fun launch(): MainActivityCore {
        PlaybackStateManager(context).clear()
        val tracks = (1..2).map { index ->
            val file = InstrumentedTestSupport.createTestWave(context, "retention-$index.wav", 12)
            files.add(file)
            Track(Uri.fromFile(file).toString(), "Retention $index", "Voltune", "Test", "Test", 12000)
        }
        TrackStore.save(context, tracks)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java)
            .putExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 2)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val host = monitor.waitForActivityWithTimeout(15000) as MainActivityCore
        activity = host
        instrumentation.removeMonitor(monitor)
        await("Library not ready") { host.librarySnapshotApplier.hasAppliedInitialSnapshot() }
        return host
    }

    private fun await(message: String, condition: () -> Boolean) {
        InstrumentedTestSupport.waitFor(message, 15000) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            ready
        }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(descendants(view.getChildAt(it))) }
    }
}
