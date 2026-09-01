package com.dumuzeyn.mp3player

import android.content.Context
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Loads immutable library snapshots without delaying the Activity's first frame. */
class LibraryLoader(
    private val context: Context,
    private val mainHandler: Handler,
) : AutoCloseable {
    fun interface Callback {
        fun loaded(snapshot: Snapshot)
    }

    fun interface HomeCallback {
        fun loaded(content: HomeContent)
    }

    class Snapshot(
        tracks: ArrayList<Track>,
        favorites: HashSet<String>,
        playlists: ArrayList<Playlist>,
        @JvmField val contentVersion: Long,
    ) {
        @JvmField val tracks = ArrayList(tracks)
        @JvmField val favorites = HashSet(favorites)
        @JvmField val playlists = ArrayList(playlists)
        @JvmField val homeContent: HomeContent =
            HomeContentBuilder().build(this.tracks, this.favorites, this.playlists)
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    @Volatile private var closed = false

    fun load(benchmarkTrackCount: Int, callback: Callback) {
        scope.launch {
            val snapshot = readSnapshot(benchmarkTrackCount)
            mainHandler.post {
                if (!closed) callback.loaded(snapshot)
            }
        }
    }

    fun refreshHome(
        favorites: Set<String>,
        playlists: List<Playlist>,
        callback: HomeCallback,
    ) {
        val favoriteSnapshot = HashSet(favorites)
        val playlistSnapshot = ArrayList(playlists)
        scope.launch {
            val content = LibraryDatabase(context.applicationContext).use { database ->
                HomeContentBuilder().build(
                    database.loadTracks(),
                    favoriteSnapshot,
                    playlistSnapshot,
                )
            }
            mainHandler.post {
                if (!closed) callback.loaded(content)
            }
        }
    }

    fun deriveHome(
        tracks: List<Track>,
        favorites: Set<String>,
        playlists: List<Playlist>,
        callback: HomeCallback,
    ) {
        val trackSnapshot = ArrayList(tracks)
        val favoriteSnapshot = HashSet(favorites)
        val playlistSnapshot = ArrayList(playlists)
        scope.launch {
            val content = HomeContentBuilder().build(
                trackSnapshot,
                favoriteSnapshot,
                playlistSnapshot,
            )
            mainHandler.post {
                if (!closed) callback.loaded(content)
            }
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
    }

    private fun readSnapshot(benchmarkTrackCount: Int): Snapshot = try {
        val appContext = context.applicationContext ?: context
        LibraryDatabase.migrateLegacyIfNeeded(appContext)
        BenchmarkLibrarySeeder.seedIfRequested(appContext, benchmarkTrackCount)
        LibraryDatabase(appContext).use { database ->
            Snapshot(
                database.loadTracks(),
                database.loadFavorites(),
                database.loadPlaylists(),
                LibraryContentVersion.read(appContext),
            )
        }
    } catch (error: RuntimeException) {
        VoltuneLog.failure("library_load_failed", error)
        Snapshot(
            ArrayList(),
            HashSet(),
            ArrayList(),
            LibraryContentVersion.read(context),
        )
    }
}
