package com.dumuzeyn.mp3player

import android.content.Context
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Reconciles persisted tracks and repairs old metadata entirely off the UI thread. */
class LibraryMaintenanceController(
    private val ownerContext: Context,
    private val mainHandler: Handler,
) : AutoCloseable {
    fun interface Callback {
        fun finished(refreshed: List<Track>, unavailable: List<Track>)
    }

    fun interface UnavailableCallback {
        fun finished(unavailable: List<Track>)
    }

    private val context: Context by lazy(LazyThreadSafetyMode.NONE) {
        ownerContext.applicationContext ?: ownerContext
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean()
    @Volatile private var closed = false

    fun run(source: List<Track>, callback: Callback) {
        if (closed || !started.compareAndSet(false, true)) return
        val snapshot = ArrayList(source)
        scope.launch { maintain(snapshot, callback) }
    }

    fun inspectUnavailable(source: List<Track>, callback: UnavailableCallback) {
        val snapshot = ArrayList(source)
        scope.launch {
            val unavailable = unavailable(snapshot)
            mainHandler.post {
                if (!closed) callback.finished(unavailable)
            }
        }
    }

    fun removeUnavailable(source: List<Track>, callback: Runnable) {
        val unavailable = ArrayList(source)
        scope.launch {
            TrackStore.applyMaintenance(
                context,
                ArrayList(),
                HashSet(),
                unavailable,
                METADATA_REVISION,
            )
            mainHandler.post {
                if (!closed) callback.run()
            }
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
    }

    private fun maintain(tracks: ArrayList<Track>, callback: Callback) {
        val refreshed = ArrayList<Track>()
        val unavailable = ArrayList<Track>()
        val checked = HashSet<String>()
        val candidates = candidatesById()
        for (track in tracks) {
            if (closed) return
            when (LibraryFileAccessManager.accessState(context, track)) {
                LibraryFileAccessManager.AccessState.UNAVAILABLE -> {
                    unavailable += track
                    continue
                }
                LibraryFileAccessManager.AccessState.DEFERRED -> continue
                LibraryFileAccessManager.AccessState.AVAILABLE -> Unit
            }
            if (track.trackId !in candidates) continue
            checked += track.trackId
            if (!needsRefresh(track)) continue
            val updated = TrackStore.refreshMetadata(context, track)
            if (metadataChanged(track, updated)) refreshed += updated
        }
        if (closed) return
        TrackStore.applyMaintenance(context, refreshed, checked, unavailable, METADATA_REVISION)
        mainHandler.post {
            if (!closed) callback.finished(refreshed, unavailable)
        }
    }

    private fun unavailable(tracks: List<Track>): ArrayList<Track> {
        val result = ArrayList<Track>()
        for (track in tracks) {
            if (closed) break
            if (
                LibraryFileAccessManager.accessState(context, track) ==
                LibraryFileAccessManager.AccessState.UNAVAILABLE
            ) {
                result += track
            }
        }
        return result
    }

    private fun candidatesById(): Map<String, Track> =
        TrackStore.loadMetadataRefreshCandidates(context, METADATA_REVISION)
            .associateBy(Track::trackId)

    private fun needsRefresh(track: Track): Boolean =
        track.durationMs <= 0 ||
            isMissing(track.artist, "Unknown artist") ||
            isMissing(track.album, "Unknown album") ||
            GenreNormalizer.isUnknown(track.genre)

    private fun isMissing(value: String?, placeholder: String): Boolean {
        val cleaned = value?.trim().orEmpty()
        return cleaned.isEmpty() ||
            cleaned.equals(placeholder, ignoreCase = true) ||
            cleaned.equals("<unknown>", ignoreCase = true)
    }

    private fun metadataChanged(before: Track, after: Track): Boolean =
        before.durationMs != after.durationMs ||
            before.artist != after.artist ||
            before.album != after.album ||
            before.albumArtist != after.albumArtist ||
            before.genre != after.genre ||
            before.year != after.year ||
            before.trackNumber != after.trackNumber ||
            before.discNumber != after.discNumber

    companion object {
        const val METADATA_REVISION = 1
    }
}
