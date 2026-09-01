package com.dumuzeyn.mp3player

/** Immutable source revision and exclusion snapshot captured before a folder scan. */
class SourceScanSession(
    @JvmField val source: LibrarySource,
    @JvmField val exclusions: ExcludedTrackIndex,
)
