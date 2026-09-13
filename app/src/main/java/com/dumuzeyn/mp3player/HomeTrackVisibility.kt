package com.dumuzeyn.mp3player

/** Keeps repeated smart-playlist membership from duplicating rows on Home. */
object HomeTrackVisibility {
    @JvmStatic
    fun takeUnseen(tracks: List<Track>, shownTracks: MutableSet<String>): ArrayList<Track> =
        tracks.filterTo(ArrayList()) { track ->
            shownTracks.add(track.trackId.ifEmpty { track.uri })
        }
}
