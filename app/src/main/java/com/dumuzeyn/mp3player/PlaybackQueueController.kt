package com.dumuzeyn.mp3player

import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager
import java.util.Collections
import java.util.Random

/** Owns library-facing queue decisions; Media3 remains the active queue owner. */
class PlaybackQueueController(
    private val host: MainActivityCore,
    private val playback: PlaybackController,
) {
    private val mutations = LibraryMutationController(host)

    fun playTrack(track: Track?) {
        if (track == null || host.libraryState.tracks.indexOf(track) < 0) return
        playback.submitQueue(arrayListOf(track), 0, 0, host.repeatMode(), true)
    }

    fun playList(source: ArrayList<Track>?, shuffle: Boolean) {
        if (source.isNullOrEmpty()) return
        val queue = ArrayList(source)
        if (shuffle) Collections.shuffle(queue, Random())
        playback.submitQueue(queue, 0, 0, host.repeatMode(), true)
    }

    fun toggleOrStart() {
        if (playback.hasPlaybackSession()) {
            playback.toggle()
        } else if (host.libraryState.tracks.isNotEmpty()) {
            playList(host.libraryState.tracks, false)
        }
    }

    fun restore(state: PlaybackStateManager.State) {
        val current = host.findTrack(state.uri)
        val queue = PlaybackQueueResolver.restore(
            host.libraryState.tracks,
            state.queueUris,
            current,
        )
        if (queue.isNotEmpty()) {
            playback.submitQueue(
                queue,
                state.index.coerceAtMost(queue.lastIndex),
                state.position,
                state.loopMode,
                state.playing,
            )
        }
    }

    fun clear() {
        PlaybackStateManager(host).clear()
        playback.clearQueue()
    }

    fun add(track: Track?) {
        if (track == null || containsUri(activeQueue(), track.uri)) return
        playback.addQueueItem(track)
    }

    fun addAll(tracks: List<Track?>) {
        val seen = activeQueue().mapTo(HashSet()) { it.uri }
        val additions = tracks.mapNotNullTo(ArrayList()) { track ->
            track?.takeIf { seen.add(it.uri) }
        }
        playback.addQueueItems(additions)
    }

    fun playNext(track: Track?) {
        if (track != null) playback.playNext(track)
    }

    fun move(from: Int, to: Int) = playback.moveQueueItem(from, to)

    fun remove(track: Track?) {
        if (track == null) return
        indexOfUri(activeQueue(), track.uri)
            .takeIf { it >= 0 }
            ?.let(playback::removeQueueItem)
    }

    fun removeFromLibrary(track: Track?) {
        val stored = track?.let { host.findTrack(it.uri) } ?: return
        mutations.removeTrack(stored)
    }

    fun removeDeletedFile(track: Track?) {
        val stored = track?.let { host.findTrack(it.uri) } ?: return
        mutations.removeDeletedFile(stored)
    }

    fun removeSource(source: LibrarySource) = mutations.removeSource(source)

    fun clearLibrary() = mutations.clearLibrary()

    fun close() = mutations.close()

    fun removeCommitted(trackIds: Set<String>, trackUris: Set<String>) {
        if (trackIds.isEmpty()) return
        PlaybackStateManager(host).removeTracks(trackIds, trackUris)
        for (index in host.playbackUiState.queue.lastIndex downTo 0) {
            if (host.playbackUiState.queue[index].trackId in trackIds) {
                host.playbackUiState.queue.removeAt(index)
            }
        }
        playback.removeQueueItems(trackIds)
    }

    fun playIndex(index: Int, position: Int) {
        val queue = ArrayList(activeQueue())
        if (queue.isNotEmpty()) {
            playback.submitQueue(
                queue,
                index.coerceIn(0, queue.lastIndex),
                position,
                host.repeatMode(),
                true,
            )
        }
    }

    fun seekIndex(index: Int) = playback.seekQueueItem(index)

    fun loopLabel(): String = when (host.repeatMode()) {
        1 -> host.tr("Song ↻", "Песня ↻")
        2 -> host.tr("List ↻", "Список ↻")
        else -> host.tr("Repeat ↻", "Повтор ↻")
    }

    fun activeQueue(): ArrayList<Track> =
        host.playbackUiState.queue.takeUnless { it.isEmpty() } ?: host.libraryState.tracks

    fun queueUris(): ArrayList<String> = activeQueue().mapTo(ArrayList()) { it.uri }

    fun isPlayingSource(source: ArrayList<Track>?): Boolean =
        host.isPlaybackPlaying() && source != null && sameOrderedQueue(source, host.playbackUiState.queue)

    fun isPlayingCollection(source: ArrayList<Track>?): Boolean =
        host.isPlaybackPlaying() && isCurrentCollection(source)

    fun isCurrentCollection(source: ArrayList<Track>?): Boolean {
        if (source.isNullOrEmpty() || host.playbackUiState.queue.size != source.size) return false
        val expected = source.mapTo(HashSet()) { it.uri }
        val active = host.playbackUiState.queue.mapTo(HashSet()) { it.uri }
        return expected.size == source.size && expected == active
    }

    fun indexOf(track: Track): Int {
        val index = indexOfUri(activeQueue(), track.uri)
        return if (index >= 0) index else host.libraryState.tracks.indexOf(track).coerceAtLeast(0)
    }

    private fun sameOrderedQueue(first: List<Track>, second: List<Track>): Boolean =
        first.size == second.size && first.indices.all { first[it].uri == second[it].uri }

    private fun containsUri(tracks: List<Track>, uri: String): Boolean =
        indexOfUri(tracks, uri) >= 0

    private fun indexOfUri(tracks: List<Track>, uri: String): Int =
        tracks.indexOfFirst { it.uri == uri }
}
