package com.dumuzeyn.mp3player;

/** One scanned track plus its stable location inside a source tree. */
final class DiscoveredTrack {
    final Track track;
    final String documentId;
    final String identityKey;

    DiscoveredTrack(Track track, LibrarySource source, String documentId) {
        this.track = track;
        this.documentId = documentId;
        this.identityKey = TrackOrigin.identity(source.sourceId, documentId);
    }
}
