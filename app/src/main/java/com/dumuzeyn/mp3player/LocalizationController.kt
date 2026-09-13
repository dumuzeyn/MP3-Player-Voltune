package com.dumuzeyn.mp3player

/** Keeps runtime language selection and navigation labels in one place. */
class LocalizationController(private val host: MainActivityCore) {
    fun text(english: String, russian: String): String = if (isEnglish()) english else russian

    fun languageName(): String = if (isEnglish()) "English" else "Русский"

    fun refreshTabLabels() {
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

    private fun isEnglish(): Boolean = host.appearanceState.language == "en"
}
