package com.dumuzeyn.mp3player

import android.view.View

/** Keeps runtime language selection and navigation labels in one place. */
class LocalizationController(private val host: MainActivityCore) {
    fun text(english: String, russian: String): String = when (host.appearanceState.language) {
        "ru" -> russian
        "en" -> english
        else -> TranslationCatalog.text(host.appearanceState.language, english)
    }

    fun languageName(): String = AppLanguages.find(host.appearanceState.language).nativeName

    fun refreshTabLabels() {
        host.window.decorView.layoutDirection = if (
            AppLanguages.find(host.appearanceState.language).rightToLeft
        ) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
        host.tabs = arrayOf(
            text("Home", "Главная"),
            text("Songs", "Песни"),
            text("Favorites", "Избранное"),
            text("Playlists", "Плейлисты"),
            text("Thematic albums", "Тематические альбомы"),
            text("Genres", "Жанры"),
            text("Artists", "Исполнители"),
            text("Albums", "Альбомы"),
            text("Folders", "Папки"),
            text("Editor", "Редактор"),
            text("Settings", "Настройки"),
        )
        host.navigationState.tabIndex = host.menuConfigurationController.visibleOrFirst(
            host.navigationState.tabIndex,
        )
    }
}
