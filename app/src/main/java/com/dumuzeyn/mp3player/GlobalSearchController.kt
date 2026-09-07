package com.dumuzeyn.mp3player

import android.os.Handler
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Debounced cancellable global search with stale-result protection. */
internal class GlobalSearchController(private val mainHandler: Handler) : AutoCloseable {
    fun interface Callback {
        fun completed(result: GlobalSearchResult)
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val engine = GlobalSearchEngine()
    private var scheduled: Runnable? = null
    private var generation = 0
    private var closed = false

    @Synchronized
    fun search(
        tracks: List<Track>,
        playlists: List<Playlist>,
        query: String,
        callback: Callback,
    ) {
        if (closed) return
        val request = ++generation
        scheduled?.let(mainHandler::removeCallbacks)
        val trackSnapshot = ArrayList(tracks)
        val playlistSnapshot = ArrayList(playlists)
        val task = Runnable {
            execute(request, trackSnapshot, playlistSnapshot, query, callback)
        }
        scheduled = task
        mainHandler.postDelayed(task, DEBOUNCE_MS)
    }

    @Synchronized
    fun cancel() {
        generation++
        scheduled?.let(mainHandler::removeCallbacks)
        scheduled = null
    }

    @Synchronized
    override fun close() {
        closed = true
        cancel()
        executor.shutdownNow()
    }

    private fun execute(
        request: Int,
        tracks: List<Track>,
        playlists: List<Playlist>,
        query: String,
        callback: Callback,
    ) {
        synchronized(this) {
            scheduled = null
            if (closed || request != generation) return
        }
        try {
            executor.execute {
                deliver(request, engine.search(tracks, playlists, query, RESULT_LIMIT), callback)
            }
        } catch (_: RejectedExecutionException) {
            // Activity is already closing.
        }
    }

    private fun deliver(request: Int, result: GlobalSearchResult, callback: Callback) {
        mainHandler.post {
            synchronized(this) {
                if (closed || request != generation) return@post
            }
            callback.completed(result)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 220L
        const val RESULT_LIMIT = 20
    }
}
