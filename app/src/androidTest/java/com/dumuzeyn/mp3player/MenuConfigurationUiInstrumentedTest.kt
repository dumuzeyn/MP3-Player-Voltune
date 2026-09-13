package com.dumuzeyn.mp3player

import android.content.Context
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuConfigurationUiInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var activity: MainActivityCore? = null

    @After
    fun cleanup() {
        activity?.let { InstrumentedTestSupport.finishActivity(instrumentation, it) }
        context.getSharedPreferences("mp3_player_ui", 0).edit().clear().commit()
    }

    @Test
    fun wheelUsesOnlyEnabledTabsAndSettingsPanelListsEverySection() {
        context.getSharedPreferences("mp3_player_ui", 0).edit().clear().commit()
        val host = InstrumentedTestSupport.launchForPlayback(instrumentation, context)
        activity = host

        instrumentation.runOnMainSync {
            host.menuConfigurationController.setEnabled(LibraryTabs.FAVORITES, false)
            host.menuConfigurationController.setEnabled(LibraryTabs.PLAYLISTS, false)
            host.refreshMenuConfiguration()
        }
        instrumentation.waitForIdleSync()

        assertEquals(
            LibraryTabs.SOUND,
            host.menuConfigurationController.adjacent(LibraryTabs.SONGS, 1),
        )
        repeat(host.tabRow.childCount) { index ->
            val tabId = (host.tabRow.getChildAt(index) as Button).tag as Int
            assertFalse(tabId == LibraryTabs.FAVORITES || tabId == LibraryTabs.PLAYLISTS)
        }

        instrumentation.runOnMainSync { host.switchTabAnimated(LibraryTabs.SONGS, 1) }
        InstrumentedTestSupport.waitFor("Songs did not open", 5_000L) {
            host.navigationState.tabIndex == LibraryTabs.SONGS &&
                !host.navigationState.tabAnimating
        }
        swipeToNext(host)
        InstrumentedTestSupport.waitFor("Swipe did not skip hidden sections", 5_000L) {
            host.navigationState.tabIndex == LibraryTabs.SOUND &&
                !host.navigationState.tabAnimating
        }

        instrumentation.runOnMainSync {
            val albums = host.menuConfigurationController.orderedTabs()
                .indexOf(LibraryTabs.ALBUMS)
            host.menuConfigurationController.move(albums, 0)
            host.refreshMenuConfiguration()
        }
        instrumentation.waitForIdleSync()
        assertEquals(LibraryTabs.ALBUMS, host.tabRow.getChildAt(0).tag as Int)
        val restored = MenuConfigurationController(host).apply { load() }
        assertEquals(LibraryTabs.ALBUMS, restored.visibleTabs().first())
        assertFalse(restored.isVisible(LibraryTabs.FAVORITES))

        instrumentation.runOnMainSync { host.menuConfigurationDialog.open() }
        val list = findRecycler(host.overlayHost)
        assertNotNull(list)
        assertEquals(LibraryTabs.ALL.size, list?.adapter?.itemCount)
    }

    private fun swipeToNext(host: MainActivityCore) {
        val startX = host.contentHost.width * 0.85f
        val endX = host.contentHost.width * 0.25f
        val y = host.contentHost.height * 0.5f
        val down = SystemClock.uptimeMillis()
        dispatch(host, MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, startX, y, 0))
        repeat(8) { index ->
            val step = index + 1
            dispatch(
                host,
                MotionEvent.obtain(
                    down,
                    down + step * 18L,
                    MotionEvent.ACTION_MOVE,
                    startX + (endX - startX) * step / 8f,
                    y,
                    0,
                ),
            )
        }
        dispatch(host, MotionEvent.obtain(down, down + 180L, MotionEvent.ACTION_UP, endX, y, 0))
    }

    private fun dispatch(host: MainActivityCore, event: MotionEvent) {
        instrumentation.runOnMainSync { host.swipeController.handle(event) }
        event.recycle()
    }

    private fun findRecycler(parent: ViewGroup): RecyclerView? {
        repeat(parent.childCount) { index ->
            val child = parent.getChildAt(index)
            if (child is RecyclerView) return child
            if (child is ViewGroup) findRecycler(child)?.let { return it }
        }
        return null
    }
}
