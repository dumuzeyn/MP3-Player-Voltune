package com.dumuzeyn.mp3player

enum class SmartPlaylistDefinition(
    @JvmField val englishName: String,
    @JvmField val russianName: String,
) {
    RECENTLY_PLAYED("Recently played", "Недавно слушал"),
    MOST_PLAYED("Most played", "Часто слушаю"),
    RECENTLY_ADDED("Recently added", "Недавно добавлено"),
    NOT_PLAYED_RECENTLY("Not played recently", "Давно не слушал"),
    NEVER_PLAYED("Never played", "Никогда не слушал"),
    MOST_LOVED("Most loved", "Самые любимые"),
}
