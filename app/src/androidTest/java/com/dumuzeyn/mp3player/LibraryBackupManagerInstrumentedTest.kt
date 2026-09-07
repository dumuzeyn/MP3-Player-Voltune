package com.dumuzeyn.mp3player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBackupManagerInstrumentedTest {
    @Test
    fun backupRoundTripRestoresPlaylistsAndSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putBoolean("animations", true)
            .putInt("cardOpacity", 73)
            .putLong("resumeWindow", 7_200_000L)
            .putFloat("particles", 0.4f)
            .putString("language", "ru")
            .putStringSet("folders", setOf("Music", "Archive"))
            .commit()

        val track = Track("content://song-a", "Song A", "Artist")
        val playlist = Playlist("Road").apply {
            uris.add(track.uri)
            uris.add("content://missing")
        }
        val backup = LibraryBackupManager.exportBackup(context, listOf(track), listOf(playlist))
        preferences.edit().clear().commit()

        val imported = LibraryBackupManager.importBackup(context, backup, listOf(track))

        assertEquals(1, imported.playlists.size)
        assertEquals("Road", imported.playlists[0].name)
        assertEquals(listOf(track.uri), imported.playlists[0].uris)
        assertTrue(preferences.getBoolean("animations", false))
        assertEquals(73, preferences.getInt("cardOpacity", 0))
        assertEquals(7_200_000L, preferences.getLong("resumeWindow", 0L))
        assertEquals(0.4f, preferences.getFloat("particles", 0f), 0.001f)
        assertEquals("ru", preferences.getString("language", ""))
        assertEquals(setOf("Music", "Archive"), preferences.getStringSet("folders", emptySet()))
    }

    @Test
    fun backupRejectsUnknownSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(IllegalArgumentException::class.java) {
            LibraryBackupManager.importBackup(
                context,
                "{\"schemaVersion\":2,\"playlists\":[]}",
                emptyList(),
            )
        }
    }

    @Test
    fun legacyBackupWithoutTypeMetadataStillImports() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        LibraryBackupManager.importBackup(
            context,
            """{"schemaVersion":1,"playlists":[],"settings":{"animations":true,"cardOpacity":73,"language":"ru"}}""",
            emptyList(),
        )

        assertTrue(preferences.getBoolean("animations", false))
        assertEquals(73, preferences.getInt("cardOpacity", 0))
        assertEquals("ru", preferences.getString("language", ""))
    }
}
