package com.dumuzeyn.mp3player;

/** Stable ownership data for a track discovered inside a SAF tree. */
final class TrackOrigin {
    final String trackId;
    final String sourceId;
    final String documentId;
    final String identityKey;

    TrackOrigin(String trackId, String sourceId, String documentId, String identityKey) {
        this.trackId = trackId;
        this.sourceId = sourceId;
        this.documentId = documentId;
        this.identityKey = identityKey;
    }

    static String identity(String sourceId, String documentId) {
        return "document:" + sourceId + ':' + documentId;
    }

    static String uriIdentity(String uri) {
        return "uri:" + (uri == null ? "" : uri);
    }
}
