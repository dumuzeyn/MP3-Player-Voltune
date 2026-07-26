package com.dumuzeyn.mp3player;

import java.util.ArrayList;

final class LibraryListController {
    private final MainActivityCore host;

    LibraryListController(MainActivityCore host) {
        this.host = host;
    }

    ArrayList<Track> currentVisibleTracks() {
        return host.navigationState.tabIndex == 1 ? filter(favoriteTracks()) : filter(host.libraryState.tracks);
    }

    ArrayList<Track> favoriteTracks() {
        ArrayList<Track> result = new ArrayList<>();
        for (Track track : host.libraryState.tracks) {
            if (host.libraryState.favorites.contains(track.uri)) {
                result.add(track);
            }
        }
        return result;
    }

    ArrayList<Track> filter(ArrayList<Track> source) {
        if (host.navigationState.search.trim().isEmpty()) {
            return source;
        }
        ArrayList<Track> result = new ArrayList<>();
        String query = Track.normalizeSearchText(host.navigationState.search);
        for (Track track : source) {
            if (matchesTrackSearch(track, query)) {
                result.add(track);
            }
        }
        return result;
    }

    boolean matchesTrackSearch(Track track, String query) {
        return track != null && track.normalizedSearchText.contains(
                query == null ? "" : query);
    }

    boolean containsSearch(String value, String query) {
        return Track.normalizeSearchText(value).contains(Track.normalizeSearchText(query));
    }
}
