package com.dumuzeyn.mp3player

internal class GenresMenuRenderer(host: MainActivityCore) : TrackGroupMenuRenderer(host) {
    override fun groupedTracks(): Map<String, ArrayList<Track>> =
        host.libraryState.homeContent.genreTracks

    override fun unknownGroupName(): String = host.tr("Unknown genre", "Неизвестный жанр")

    override fun cardOpacity(): Int = host.appearanceState.genreCardOpacity
}
