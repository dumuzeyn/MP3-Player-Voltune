package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackMetadataRefreshInstrumentedTest {
    @Test
    fun missingEmbeddedAlbumReplacesMediaStoreFolderFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "metadata-without-album.mp3")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("audio-formats/tone.mp3")
                .use { input -> source.outputStream().use(input::copyTo) }
            val indexed = Track(
                Uri.fromFile(source).toString(),
                "Tone",
                "Edited artist",
                "Music",
                "Edited genre",
                0,
            )

            val refreshed = TrackStore.refreshFolderAlbumMetadata(context, indexed)

            assertEquals("Unknown album", refreshed.album)
            assertEquals("Edited artist", refreshed.artist)
            assertEquals("Edited genre", refreshed.genre)
        } finally {
            source.delete()
        }
    }
}
