package com.dumuzeyn.mp3player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuConfigurationModelTest {
    @Test
    fun hiddenTabsAreSkippedAndSettingsRemainsAvailable() {
        val model = MenuConfigurationModel()

        assertTrue(model.setEnabled(LibraryTabs.FAVORITES, false))
        assertTrue(model.setEnabled(LibraryTabs.PLAYLISTS, false))
        assertEquals(LibraryTabs.SOUND, model.adjacent(LibraryTabs.SONGS, 1))
        assertFalse(model.visibleTabs().contains(LibraryTabs.FAVORITES))
        assertFalse(model.setEnabled(LibraryTabs.SETTINGS, false))
        assertTrue(model.isVisible(LibraryTabs.SETTINGS))
    }

    @Test
    fun orderIsSanitizedAndMovableWithoutChangingTabIdentity() {
        val model = MenuConfigurationModel(
            listOf(LibraryTabs.ALBUMS, LibraryTabs.ALBUMS, 99, LibraryTabs.HOME),
            LibraryTabs.ALL,
        )

        assertEquals(LibraryTabs.ALBUMS, model.orderedTabs().first())
        assertEquals(LibraryTabs.ALL.size, model.orderedTabs().distinct().size)
        val songs = model.orderedTabs().indexOf(LibraryTabs.SONGS)
        assertTrue(model.move(songs, 0))
        assertEquals(LibraryTabs.SONGS, model.visibleTabs().first())
        assertEquals(LibraryTabs.ALBUMS, model.adjacent(LibraryTabs.SONGS, 1))
    }
}
