package com.dumuzeyn.mp3player

/** One scanned track plus its stable location inside a source tree. */
class DiscoveredTrack(
    @JvmField val track: Track,
    source: LibrarySource,
    @JvmField val documentId: String,
) {
    @JvmField val identityKey: String = TrackOrigin.identity(source.sourceId, documentId)
}
