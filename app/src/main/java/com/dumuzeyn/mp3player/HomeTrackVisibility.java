package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Keeps repeated smart-playlist membership from duplicating rows on Home. */
final class HomeTrackVisibility {
    private HomeTrackVisibility() {
    }

    static ArrayList<Track> takeUnseen(List<Track> tracks, Set<String> shownTracks) {
        ArrayList<Track> unique = new ArrayList<>();
        for (Track track : tracks) {
            String key = track.trackId == null || track.trackId.isEmpty()
                    ? track.uri : track.trackId;
            if (shownTracks.add(key)) {
                unique.add(track);
            }
        }
        return unique;
    }
}
