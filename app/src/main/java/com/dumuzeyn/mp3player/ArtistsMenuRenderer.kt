package com.dumuzeyn.mp3player

internal class ArtistsMenuRenderer(host: MainActivityCore) : TrackGroupMenuRenderer(host) {
    override fun groupedTracks(): Map<String, ArrayList<Track>> =
        host.libraryState.homeContent.artistTracks

    override fun unknownGroupName(): String =
        host.tr("Unknown artist", "Неизвестный исполнитель")

    override fun cardOpacity(): Int = host.appearanceState.artistCardOpacity
}
