package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlphabetRailInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var activity: MainActivityCore? = null

    @After fun close() {
        activity?.let { InstrumentedTestSupport.finishActivity(instrumentation, it) }
    }

    @Test fun mixedAlphabetRailJumpsWithoutCoveringSongText() {
        val titles = buildList {
            repeat(54) { add(if (it % 2 == 0) "Alpha $it" else "beta $it") }
            add("Блюз")
            add("Яблоко")
            add("42")
        }
        val tracks = titles.mapIndexed { index, title ->
            Track(Uri.parse("content://alphabet/$index").toString(), title, "Artist", "Album", "Genre", 1000)
        }
        TrackStore.save(context, tracks)
        context.getSharedPreferences("mp3_player_ui", 0).edit().putBoolean("particlesEnabled", false).commit()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java)
            .putExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, tracks.size)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        activity = monitor.waitForActivityWithTimeout(15000) as MainActivityCore
        instrumentation.removeMonitor(monitor)
        val host = checkNotNull(activity)
        InstrumentedTestSupport.waitFor("Library did not load", 15000) {
            host.librarySnapshotApplier.hasAppliedInitialSnapshot()
        }
        instrumentation.runOnMainSync { host.switchTabAnimated(LibraryTabs.SONGS, 1) }
        InstrumentedTestSupport.waitFor("Songs did not open", 5000) {
            host.navigationState.tabIndex == LibraryTabs.SONGS && host.songsView?.visibility == View.VISIBLE
                && !host.navigationState.tabAnimating
                && (host.songsView?.recyclerView()?.adapter?.itemCount ?: 0) > 50
        }
        val rail = find(host.songsView!!, AlphabetRailView::class.java)
        assertNotNull(rail)
        assertEquals(View.VISIBLE, rail!!.visibility)
        assertTrue(rail.width <= host.dp(28))
        assertTrue(rail.height >= host.songsView!!.height - host.dp(2))
        val initialThumbCenter = thumbCenter(host, rail)
        instrumentation.runOnMainSync {
            val time = android.os.SystemClock.uptimeMillis()
            val targetY = rail.height * 0.70f
            rail.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                rail.width / 2f, targetY, 0))
            rail.dispatchTouchEvent(MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP,
                rail.width / 2f, targetY, 0))
        }
        assertTrue(rail.contentDescription.toString().startsWith("Я"))
        assertTrue(thumbCenter(host, rail) > initialThumbCenter + rail.height / 3)
        val manager = host.songsView!!.recyclerView().layoutManager as LinearLayoutManager
        InstrumentedTestSupport.waitFor("Alphabet rail did not move the list", 5000) {
            var position = 0
            instrumentation.runOnMainSync { position = manager.findFirstVisibleItemPosition() }
            position > 1
        }
        instrumentation.runOnMainSync { manager.scrollToPositionWithOffset(1, 0) }
        InstrumentedTestSupport.waitFor("Rail selection did not follow list scrolling", 5000) {
            rail.contentDescription.toString().startsWith("A")
        }
    }

    private fun thumbCenter(host: MainActivityCore, rail: AlphabetRailView): Int {
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            bitmap = Bitmap.createBitmap(rail.width, rail.height, Bitmap.Config.ARGB_8888)
            rail.draw(Canvas(bitmap))
        }
        val x = rail.width - host.dp(3)
        val rows = (0 until rail.height).filter { Color.alpha(bitmap.getPixel(x, it)) > 128 }
        assertTrue(rows.size in host.dp(20)..host.dp(44))
        val colors = rows.map { bitmap.getPixel(x, it) }
        assertTrue(colors.any { Color.blue(it) > Color.red(it) + 80 })
        assertTrue(colors.any { Color.red(it) > Color.blue(it) + 80 })
        bitmap.recycle()
        return (rows.first() + rows.last()) / 2
    }

    private fun <T : View> find(view: View, type: Class<T>): T? {
        if (type.isInstance(view)) return type.cast(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            find(view.getChildAt(index), type)?.let { return it }
        }
        return null
    }
}
