package com.dumuzeyn.mp3player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Persists collection edits without blocking taps, scrolling, or overlay animations. */
class LibraryPersistenceController(private val context: Context) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val lock = Any()
    private var pending: Snapshot? = null
    private var workerScheduled = false
    @Volatile private var closed = false

    fun save(favorites: Set<String>, playlists: List<Playlist>) {
        if (closed) return
        val scheduleWorker = synchronized(lock) {
            if (closed) return@synchronized false
            pending = Snapshot(favorites, playlists)
            if (workerScheduled) {
                false
            } else {
                workerScheduled = true
                true
            }
        }
        if (scheduleWorker) scope.launch { drain() }
    }

    fun close() {
        closed = true
        job.complete()
    }

    private fun drain() {
        while (true) {
            val snapshot = synchronized(lock) {
                pending.also { pending = null }.also {
                    if (it == null) workerScheduled = false
                }
            } ?: return
            try {
                LibraryDatabase(context).use { database ->
                    database.saveCollections(snapshot.favorites, snapshot.playlists)
                }
            } catch (error: RuntimeException) {
                VoltuneLog.failure("collection_save_failed", error)
            }
        }
    }

    private class Snapshot(favorites: Set<String>, playlists: List<Playlist>) {
        val favorites = HashSet(favorites)
        val playlists = playlists.mapTo(ArrayList()) { source ->
            Playlist(source.name).also { it.uris.addAll(source.uris) }
        }
    }
}
