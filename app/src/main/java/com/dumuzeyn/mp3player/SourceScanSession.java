package com.dumuzeyn.mp3player;

/** Immutable source revision and exclusion snapshot captured before a folder scan. */
final class SourceScanSession {
    final LibrarySource source;
    final ExcludedTrackIndex exclusions;

    SourceScanSession(LibrarySource source, ExcludedTrackIndex exclusions) {
        this.source = source;
        this.exclusions = exclusions;
    }
}
