package com.dumuzeyn.mp3player

internal class AlbumsMenuRenderer(host: MainActivityCore) : TrackGroupMenuRenderer(host) {
    override fun groupedTracks(): Map<String, ArrayList<Track>> =
        host.libraryState.homeContent.albumTracks

    override fun unknownGroupName(): String = host.tr("Unknown album", "Неизвестный альбом")

    override fun cardOpacity(): Int = host.appearanceState.albumCardOpacity
}
