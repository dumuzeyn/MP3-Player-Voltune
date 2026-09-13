package com.dumuzeyn.mp3player

import kotlinx.coroutines.flow.StateFlow

/** Read-only Media3 projection used by player UI. */
interface PlaybackStateProvider {
    fun state(): StateFlow<PlaybackSnapshot>
    fun currentSnapshot(): PlaybackSnapshot
    fun currentTrack(): Track?
    fun isCurrentTrack(track: Track?): Boolean
    fun isPlaying(): Boolean
    fun repeatMode(): Int
    fun activeQueue(): List<Track>
    fun queueIndex(track: Track): Int
}
