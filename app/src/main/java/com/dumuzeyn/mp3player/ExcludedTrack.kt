package com.dumuzeyn.mp3player

/** Persistent user decision to keep one physical audio file out of Voltune. */
class ExcludedTrack(
    @JvmField val identityKey: String,
    sourceId: String?,
    documentId: String?,
    uri: String?,
    @JvmField val fileSize: Long,
    @JvmField val lastModified: Long,
    fingerprint: String?,
) {
    @JvmField val sourceId: String = sourceId.orEmpty()
    @JvmField val documentId: String = documentId.orEmpty()
    @JvmField val uri: String = uri.orEmpty()
    @JvmField val fingerprint: String = fingerprint.orEmpty()

    companion object {
        @JvmStatic
        fun from(track: Track, origin: TrackOrigin?): ExcludedTrack = ExcludedTrack(
            origin?.identityKey ?: TrackOrigin.uriIdentity(track.uri),
            origin?.sourceId,
            origin?.documentId,
            track.uri,
            track.fileSize,
            track.lastModified,
            track.fingerprint,
        )
    }
}
