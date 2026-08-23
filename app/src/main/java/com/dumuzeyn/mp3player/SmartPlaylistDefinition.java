package com.dumuzeyn.mp3player;

enum SmartPlaylistDefinition {
    RECENTLY_PLAYED("Recently played", "Недавно слушал"),
    MOST_PLAYED("Most played", "Часто слушаю"),
    RECENTLY_ADDED("Recently added", "Недавно добавлено"),
    NOT_PLAYED_RECENTLY("Not played recently", "Давно не слушал"),
    NEVER_PLAYED("Never played", "Никогда не слушал"),
    MOST_LOVED("Most loved", "Самые любимые");

    final String englishName;
    final String russianName;

    SmartPlaylistDefinition(String englishName, String russianName) {
        this.englishName = englishName;
        this.russianName = russianName;
    }
}
