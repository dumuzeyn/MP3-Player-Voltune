package com.dumuzeyn.mp3player;

import java.util.List;

/** Finds one high-confidence physical duplicate without merging ambiguous matches. */
final class TrackDuplicatePolicy {
    private TrackDuplicatePolicy() {
    }

    static int duplicateIndex(List<Track> library, Track discovered) {
        List<Track> matches = TrackRelinker.candidates(library, discovered);
        if (matches.size() != 1) {
            return -1;
        }
        Track match = matches.get(0);
        for (int index = 0; index < library.size(); index++) {
            if (library.get(index).trackId.equals(match.trackId)) {
                return index;
            }
        }
        return -1;
    }

    static int sameLocationIndex(List<Track> library, Track discovered) {
        for (int index = 0; index < library.size(); index++) {
            Track existing = library.get(index);
            if (existing.trackId.equals(discovered.trackId)
                    || existing.uri.equals(discovered.uri)) {
                return index;
            }
        }
        return -1;
    }

    static Track withStableIdentity(Track stable, Track fresh) {
        return new Track(stable.trackId, fresh.uri, fresh.title, fresh.artist, fresh.album,
                fresh.albumArtist, fresh.genre, fresh.year, fresh.trackNumber,
                fresh.discNumber, fresh.durationMs, fresh.fileSize, fresh.lastModified,
                fresh.fingerprint, stable.playCount, stable.skipCount, stable.dateAdded,
                stable.lastPlayedAt, stable.lastCompletedAt);
    }
}
