package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tracks and permissions affected by one committed library mutation. */
final class RemovedLibraryItems {
    final Set<String> trackIds = new HashSet<>();
    final Set<String> trackUris = new HashSet<>();
    final List<LibrarySource> sources = new ArrayList<>();
    boolean clearQueue;

    void add(String trackId, String uri) {
        trackIds.add(trackId);
        trackUris.add(uri);
    }
}
