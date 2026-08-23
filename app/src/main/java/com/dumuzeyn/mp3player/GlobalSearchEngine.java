package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class GlobalSearchEngine {
    GlobalSearchResult search(List<Track> tracks, List<Playlist> playlists, String query,
            int categoryLimit) {
        String normalized = Track.normalizeSearchText(query);
        if (normalized.isEmpty()) {
            return GlobalSearchResult.empty();
        }
        ArrayList<Track> songs = new ArrayList<>();
        LinkedHashMap<String, String> artists = new LinkedHashMap<>();
        LinkedHashMap<String, String> albums = new LinkedHashMap<>();
        LinkedHashMap<String, String> genres = new LinkedHashMap<>();
        ArrayList<Playlist> matchingPlaylists = new ArrayList<>();
        for (Track track : tracks) {
            if (track.normalizedSearchText.contains(normalized)) {
                addLimited(songs, track, categoryLimit);
            }
            addGroup(artists, track.artist, normalized, categoryLimit);
            addGroup(albums, track.album, normalized, categoryLimit);
            addGroup(genres, track.genre, normalized, categoryLimit);
        }
        for (Playlist playlist : playlists) {
            if (Track.normalizeSearchText(playlist.name).contains(normalized)) {
                addLimited(matchingPlaylists, playlist, categoryLimit);
            }
        }
        return new GlobalSearchResult(songs, new ArrayList<>(artists.values()),
                new ArrayList<>(albums.values()), new ArrayList<>(genres.values()),
                matchingPlaylists);
    }

    private static void addGroup(LinkedHashMap<String, String> target, String value,
            String query, int limit) {
        String key = Track.normalizeSearchText(value);
        if (!key.isEmpty() && key.contains(query)
                && (limit <= 0 || target.size() < limit)) {
            target.put(key, value);
        }
    }

    private static <T> void addLimited(List<T> target, T value, int limit) {
        if (limit <= 0 || target.size() < limit) {
            target.add(value);
        }
    }
}
