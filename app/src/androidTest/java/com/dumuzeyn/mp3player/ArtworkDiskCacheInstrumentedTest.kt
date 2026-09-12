package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtworkDiskCacheInstrumentedTest {
    @Test fun artworkSurvivesMemoryLoaderReplacementInPersistentStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = "persistent-artwork-${System.nanoTime()}"
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(52, 88, 210))
        }
        ArtworkDiskCache(context).write(key, bitmap)
        bitmap.recycle()

        val restored = ArtworkDiskCache(context).read(key)
        assertNotNull(restored)
        assertEquals(24, restored!!.width)
        assertEquals(24, restored.height)
        assertTrue(File(context.filesDir, "artwork-v2").isDirectory)
        restored.recycle()
    }

    @Test fun thematicGroupUsesLaterTrackWhenFirstTrackHasNoArtwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val withoutArtwork = copyAsset(instrumentation.context, context, "tone.mp3")
        val withArtwork = copyAsset(instrumentation.context, context, "tone-covered.mp3")
        val tracks = listOf(
            Track(Uri.fromFile(withoutArtwork).toString(), "No artwork", "Artist", "Album", "", 2000),
            Track(Uri.fromFile(withArtwork).toString(), "Artwork", "Artist", "Album", "", 2000),
        )
        val loader = CoverLoader(context, Handler(Looper.getMainLooper()))
        val prefetched = CountDownLatch(1)
        loader.prefetchGroupCovers(listOf(tracks)) { prefetched.countDown() }
        assertTrue("Group artwork was not prefetched", prefetched.await(8, TimeUnit.SECONDS))

        lateinit var view: ImageView
        instrumentation.runOnMainSync {
            view = ImageView(context)
            loader.loadBest(view, tracks, Color.MAGENTA, CoverLoader.THUMB_SIZE)
        }
        instrumentation.waitForIdleSync()
        val bitmap = (view.drawable as? BitmapDrawable)?.bitmap
        assertNotNull("Thematic group kept its fallback instead of the second track cover", bitmap)
        val pixel = bitmap!!.getPixel(0, 0)
        assertTrue(kotlin.math.abs(Color.red(pixel) - 52) <= 3)
        assertTrue(kotlin.math.abs(Color.green(pixel) - 88) <= 3)
        assertTrue(kotlin.math.abs(Color.blue(pixel) - 210) <= 3)
        loader.close()
    }

    private fun copyAsset(source: Context, target: Context, name: String): File {
        val output = File(target.cacheDir, "artwork-test-$name")
        source.assets.open("audio-formats/$name").use { input ->
            output.outputStream().use(input::copyTo)
        }
        return output
    }
}
