package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongsScrollbarInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var activity: MainActivityCore? = null

    @After fun close() {
        activity?.let { InstrumentedTestSupport.finishActivity(instrumentation, it) }
    }

    @Test fun songsUseTheSameFadingScrollbarAndInsetsAsTheLibrary() {
        val tracks = (0 until 57).map { index ->
            Track(
                Uri.parse("content://scrollbar/$index").toString(),
                "Track ${index.toString().padStart(2, '0')}",
                "Artist",
                "Album",
                "Genre",
                1000,
            )
        }
        TrackStore.save(context, tracks)
        context.getSharedPreferences("mp3_player_ui", 0).edit()
            .putBoolean("particlesEnabled", false)
            .commit()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, tracks.size)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        activity = monitor.waitForActivityWithTimeout(15000) as MainActivityCore
        instrumentation.removeMonitor(monitor)
        val host = checkNotNull(activity)
        InstrumentedTestSupport.waitFor("Library did not load", 15000) {
            host.librarySnapshotApplier.hasAppliedInitialSnapshot()
        }
        instrumentation.runOnMainSync { host.switchTabAnimated(LibraryTabs.SONGS, 1) }
        InstrumentedTestSupport.waitFor("Songs did not open", 5000) {
            host.navigationState.tabIndex == LibraryTabs.SONGS &&
                host.songsView?.visibility == View.VISIBLE &&
                !host.navigationState.tabAnimating &&
                (host.songsView?.recyclerView()?.adapter?.itemCount ?: 0) > 50
        }

        val songs = checkNotNull(host.songsView)
        val recycler = songs.recyclerView()
        assertEquals(1, songs.childCount)
        assertTrue(recycler.isVerticalScrollBarEnabled)
        assertTrue(recycler.isScrollbarFadingEnabled)
        assertEquals(View.SCROLLBARS_INSIDE_OVERLAY, recycler.scrollBarStyle)
        assertEquals(host.dp(6), recycler.paddingLeft)
        assertEquals(host.dp(6), recycler.paddingRight)

        val card = songs.findViewById<View>(R.id.song_card)
        assertEquals(host.dp(64), card.height)
        instrumentation.runOnMainSync {
            recycler.scrollBy(0, host.dp(160))
        }
        capture(host, "songs-scrollbar.png")

        val manager = recycler.layoutManager as LinearLayoutManager
        instrumentation.runOnMainSync {
            manager.scrollToPositionWithOffset(recycler.adapter!!.itemCount - 1, 0)
        }
        InstrumentedTestSupport.waitFor("Last song was not reachable", 5000) {
            manager.findLastVisibleItemPosition() == recycler.adapter!!.itemCount - 1
        }
    }

    private fun capture(host: MainActivityCore, name: String) {
        val bitmap = Bitmap.createBitmap(host.root.width, host.root.height, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { host.root.draw(Canvas(bitmap)) }
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
