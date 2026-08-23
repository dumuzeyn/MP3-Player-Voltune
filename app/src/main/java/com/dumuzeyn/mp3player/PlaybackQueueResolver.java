package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;

final class PlaybackQueueResolver {
    private PlaybackQueueResolver() {
    }

    static ArrayList<Track> restore(List<Track> library, String queueJson, Track fallback) {
        ArrayList<String> savedUris = new ArrayList<>();
        try {
            JSONArray savedQueue = new JSONArray(queueJson == null ? "[]" : queueJson);
            for (int index = 0; index < savedQueue.length(); index++) {
                String uri = savedQueue.optString(index, "");
                if (!uri.isEmpty()) {
                    savedUris.add(uri);
                }
            }
        } catch (Exception ignored) {
            // A damaged saved queue falls back to the current track.
        }
        return restore(library, savedUris, fallback);
    }

    static ArrayList<Track> restore(List<Track> library, List<String> savedUris, Track fallback) {
        Map<String, Track> tracksByUri = new HashMap<>();
        for (Track track : library) {
            tracksByUri.put(track.uri, track);
            tracksByUri.put(track.trackId, track);
        }

        ArrayList<Track> restored = new ArrayList<>();
        for (String uri : savedUris) {
            Track track = tracksByUri.get(uri);
            if (track != null) {
                restored.add(track);
            }
        }
        if (restored.isEmpty() && fallback != null) {
            restored.add(fallback);
        }
        return restored;
    }
}
