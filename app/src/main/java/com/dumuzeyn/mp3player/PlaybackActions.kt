package com.dumuzeyn.mp3player

/** Commands exposed to playback UI without exposing MediaController internals. */
interface PlaybackActions {
    fun playTrack(track: Track)
    fun playTracks(tracks: List<Track>, shuffle: Boolean)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun cycleRepeatMode()
}
