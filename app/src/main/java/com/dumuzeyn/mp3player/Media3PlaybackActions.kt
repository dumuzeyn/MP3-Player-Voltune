package com.dumuzeyn.mp3player

/** Routes UI commands to the existing Media3 playback components. */
class Media3PlaybackActions(
    private val queue: PlaybackQueueController,
    private val playback: PlaybackController,
) : PlaybackActions {
    override fun playTrack(track: Track) = queue.playTrack(track)

    override fun playTracks(tracks: List<Track>, shuffle: Boolean) =
        queue.playList(ArrayList(tracks), shuffle)

    override fun togglePlayPause() = queue.toggleOrStart()
    override fun next() = playback.next()
    override fun previous() = playback.previous()

    override fun seekTo(positionMs: Long) = playback.seekTo(
        positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    )

    override fun cycleRepeatMode() = playback.cycleRepeatMode()
}
