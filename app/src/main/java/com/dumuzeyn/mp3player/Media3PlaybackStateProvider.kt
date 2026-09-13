package com.dumuzeyn.mp3player

import java.util.Collections
import kotlinx.coroutines.flow.StateFlow

/** Reads the Activity projection of the authoritative Media3 state. */
class Media3PlaybackStateProvider(
    private val library: LibraryState,
    private val playback: PlaybackUiState,
) : PlaybackStateProvider {
    override fun state(): StateFlow<PlaybackSnapshot> = playback.state()
    override fun currentSnapshot(): PlaybackSnapshot = playback.snapshot()
    override fun currentTrack(): Track? = library.tracks.getOrNull(playback.currentTrackIndex(library))
    override fun isCurrentTrack(track: Track?): Boolean = currentTrack()?.uri == track?.uri
    override fun isPlaying(): Boolean = playback.isPlaying()
    override fun repeatMode(): Int = playback.repeatMode()
    override fun activeQueue(): List<Track> = Collections.unmodifiableList(ArrayList(playback.queue))
    override fun queueIndex(track: Track): Int = playback.queue.indexOf(track)
}
