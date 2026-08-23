package com.dumuzeyn.mp3player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory exclusion lookup used by scans of thousands of files. */
final class ExcludedTrackIndex {
    private final Map<String, ExcludedTrack> byIdentity = new HashMap<>();
    private final Map<String, ExcludedTrack> byFingerprint = new HashMap<>();

    ExcludedTrackIndex(List<ExcludedTrack> excluded) {
        for (ExcludedTrack item : excluded) {
            byIdentity.put(item.identityKey, item);
            if (!item.fingerprint.isEmpty()) {
                byFingerprint.put(item.fingerprint, item);
            }
        }
    }

    boolean contains(String identityKey, Track track) {
        if (byIdentity.containsKey(identityKey)
                || byIdentity.containsKey(TrackOrigin.uriIdentity(track.uri))) {
            return true;
        }
        ExcludedTrack sameContent = byFingerprint.get(track.fingerprint);
        return sameContent != null && track.fileSize > 0L
                && sameContent.fileSize == track.fileSize;
    }
}
