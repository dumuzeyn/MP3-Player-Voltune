package com.dumuzeyn.mp3player;

/** Persistent user decision to keep one physical audio file out of Voltune. */
final class ExcludedTrack {
    final String identityKey;
    final String sourceId;
    final String documentId;
    final String uri;
    final long fileSize;
    final long lastModified;
    final String fingerprint;

    ExcludedTrack(String identityKey, String sourceId, String documentId, String uri,
            long fileSize, long lastModified, String fingerprint) {
        this.identityKey = identityKey;
        this.sourceId = sourceId == null ? "" : sourceId;
        this.documentId = documentId == null ? "" : documentId;
        this.uri = uri == null ? "" : uri;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.fingerprint = fingerprint == null ? "" : fingerprint;
    }

    static ExcludedTrack from(Track track, TrackOrigin origin) {
        String key = origin == null ? TrackOrigin.uriIdentity(track.uri) : origin.identityKey;
        return new ExcludedTrack(key, origin == null ? "" : origin.sourceId,
                origin == null ? "" : origin.documentId, track.uri, track.fileSize,
                track.lastModified, track.fingerprint);
    }
}
