package com.dumuzeyn.mp3player

/** In-memory exclusion lookup used by scans of thousands of files. */
class ExcludedTrackIndex(excluded: List<ExcludedTrack>) {
    private val byIdentity = HashMap<String, ExcludedTrack>()
    private val byFingerprint = HashMap<String, ExcludedTrack>()

    init {
        excluded.forEach { item ->
            byIdentity[item.identityKey] = item
            if (item.fingerprint.isNotEmpty()) byFingerprint[item.fingerprint] = item
        }
    }

    fun contains(identityKey: String, track: Track): Boolean {
        if (identityKey in byIdentity || TrackOrigin.uriIdentity(track.uri) in byIdentity) return true
        val sameContent = byFingerprint[track.fingerprint]
        return sameContent != null && track.fileSize > 0L && sameContent.fileSize == track.fileSize
    }

    fun containsIdentity(identityKey: String): Boolean = identityKey in byIdentity
}
