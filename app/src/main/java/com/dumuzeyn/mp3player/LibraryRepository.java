package com.dumuzeyn.mp3player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Indexed in-memory access to the library and collection persistence boundary. */
final class LibraryRepository {
    interface Persistence {
        void save(Set<String> favorites, List<Playlist> playlists);
    }

    private final List<Track> tracks;
    private final Set<String> favorites;
    private final List<Playlist> playlists;
    private final Persistence persistence;
    private final Map<String, Track> tracksByUri = new HashMap<>();
    private final Map<String, Track> tracksById = new HashMap<>();

    LibraryRepository(List<Track> tracks, Set<String> favorites, List<Playlist> playlists,
            Persistence persistence) {
        this.tracks = tracks;
        this.favorites = favorites;
        this.playlists = playlists;
        this.persistence = persistence;
        reindex();
    }

    void reindex() {
        tracksByUri.clear();
        tracksById.clear();
        for (Track track : tracks) {
            tracksByUri.put(track.uri, track);
            tracksById.put(track.trackId, track);
        }
    }

    Track find(String uriOrId) {
        if (uriOrId == null || uriOrId.isEmpty()) {
            return null;
        }
        Track byUri = tracksByUri.get(uriOrId);
        return byUri != null ? byUri : tracksById.get(uriOrId);
    }

    boolean toggleFavorite(Track track) {
        if (track == null) {
            return false;
        }
        boolean favorite;
        if (favorites.remove(track.uri)) {
            favorite = false;
        } else {
            favorites.add(track.uri);
            favorite = true;
        }
        persistCollections();
        return favorite;
    }

    void persistCollections() {
        persistence.save(favorites, playlists);
    }
}
