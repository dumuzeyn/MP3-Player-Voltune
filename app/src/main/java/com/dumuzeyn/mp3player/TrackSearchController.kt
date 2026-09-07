package com.dumuzeyn.mp3player

import android.os.Handler
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Debounces and filters immutable track snapshots away from the UI thread. */
internal class TrackSearchController(private val mainHandler: Handler) : AutoCloseable {
    fun interface Callback {
        fun filtered(tracks: List<Track>)
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val generations = HashMap<String, Int>()
    private val scheduled = HashMap<String, Runnable>()
    private var closed = false

    fun filter(owner: String, source: List<Track>, query: String, callback: Callback) {
        filter(owner, source, query, DEFAULT_DEBOUNCE_MS, callback)
    }

    fun filterImmediately(owner: String, source: List<Track>, query: String, callback: Callback) {
        filter(owner, source, query, 0L, callback)
    }

    fun cancel(owner: String) {
        val pending = synchronized(this) {
            nextGeneration(owner)
            scheduled.remove(owner)
        }
        if (pending != null) mainHandler.removeCallbacks(pending)
    }

    @Synchronized
    override fun close() {
        closed = true
        scheduled.values.forEach(mainHandler::removeCallbacks)
        scheduled.clear()
        generations.clear()
        executor.shutdownNow()
    }

    private fun filter(
        owner: String,
        source: List<Track>,
        query: String,
        delayMs: Long,
        callback: Callback,
    ) {
        val snapshot = ArrayList(source)
        val normalizedQuery = Track.normalizeSearchText(query)
        val pending = synchronized(this) {
            if (closed) return
            val generation = nextGeneration(owner)
            val previous = scheduled.remove(owner)
            val task = Runnable {
                executeFilter(owner, generation, snapshot, normalizedQuery, callback)
            }
            scheduled[owner] = task
            PendingFilter(previous, task)
        }
        pending.previous?.let(mainHandler::removeCallbacks)
        if (delayMs <= 0L || normalizedQuery.isEmpty()) {
            pending.task.run()
        } else {
            mainHandler.postDelayed(pending.task, delayMs)
        }
    }

    private fun executeFilter(
        owner: String,
        generation: Int,
        snapshot: ArrayList<Track>,
        normalizedQuery: String,
        callback: Callback,
    ) {
        synchronized(this) {
            scheduled.remove(owner)
            if (closed || currentGeneration(owner) != generation) return
        }
        if (normalizedQuery.isEmpty()) {
            deliver(owner, generation, snapshot, callback)
            return
        }
        try {
            executor.execute {
                val result = snapshot.filterTo(ArrayList()) { track ->
                    track.normalizedSearchText.contains(normalizedQuery)
                }
                deliver(owner, generation, result, callback)
            }
        } catch (_: RejectedExecutionException) {
            // Activity is already closing.
        }
    }

    private fun deliver(owner: String, generation: Int, result: List<Track>, callback: Callback) {
        val immutable = Collections.unmodifiableList(ArrayList(result))
        mainHandler.post {
            synchronized(this) {
                if (closed || currentGeneration(owner) != generation) return@post
            }
            callback.filtered(immutable)
        }
    }

    private fun nextGeneration(owner: String): Int {
        val next = currentGeneration(owner) + 1
        generations[owner] = next
        return next
    }

    private fun currentGeneration(owner: String): Int = generations[owner] ?: 0

    private data class PendingFilter(val previous: Runnable?, val task: Runnable)

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 200L
    }
}
