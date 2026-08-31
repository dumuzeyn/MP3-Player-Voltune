package com.dumuzeyn.mp3player;

/** Keeps runtime language selection and navigation labels in one place. */
final class LocalizationController {
    private final MainActivityCore host;

    LocalizationController(MainActivityCore host) {
        this.host = host;
    }

    String text(String english, String russian) {
        return isEnglish() ? english : russian;
    }

    String languageName() {
        return isEnglish() ? "English" : "Русский";
    }

    void refreshTabLabels() {
        host.tabs = new String[]{text("Home", "Главная"), text("Songs", "Песни"),
                text("Favorites", "Избранное"), text("Playlists", "Плейлисты"),
                text("Similar", "Похожие"), text("Genres", "Жанры"),
                text("Artists", "Исполнители"),
                text("Albums", "Альбомы"), text("Folders", "Папки"),
                text("Settings", "Настройки")};
        if (host.navigationState.tabIndex >= host.tabs.length) {
            host.navigationState.tabIndex = LibraryTabs.HOME;
        }
    }

    private boolean isEnglish() {
        return "en".equals(host.appearanceState.language);
    }
}
