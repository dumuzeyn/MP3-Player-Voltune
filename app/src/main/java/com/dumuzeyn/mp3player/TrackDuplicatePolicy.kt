package com.dumuzeyn.mp3player

/** Finds one high-confidence physical duplicate without merging ambiguous matches. */
object TrackDuplicatePolicy {
    @JvmStatic
    fun duplicateIndex(library: List<Track>, discovered: Track): Int {
        val matches = TrackRelinker.candidates(library, discovered)
        if (matches.size != 1) return -1
        val matchId = matches.single().trackId
        return library.indexOfFirst { it.trackId == matchId }
    }

    @JvmStatic
    fun sameLocationIndex(library: List<Track>, discovered: Track): Int =
        library.indexOfFirst { it.trackId == discovered.trackId || it.uri == discovered.uri }

    @JvmStatic
    fun withStableIdentity(stable: Track, fresh: Track): Track = Track(
        stable.trackId,
        fresh.uri,
        fresh.title,
        fresh.artist,
        fresh.album,
        fresh.albumArtist,
        fresh.genre,
        fresh.year,
        fresh.trackNumber,
        fresh.discNumber,
        fresh.durationMs,
        fresh.fileSize,
        fresh.lastModified,
        fresh.fingerprint,
        stable.playCount,
        stable.skipCount,
        stable.dateAdded,
        stable.lastPlayedAt,
        stable.lastCompletedAt,
    )
}
