package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HomeContent {
    final List<Track> recentlyPlayed;
    final List<Track> recentlyAdded;
    final List<Track> mostPlayed;
    final List<Track> favorites;
    final List<Track> allFavorites;
    final List<Playlist> playlists;
    final List<String> artists;
    final List<String> albums;
    final Map<String, ArrayList<Track>> folders;
    final Map<String, ArrayList<Track>> artistTracks;
    final Map<String, ArrayList<Track>> albumTracks;
    final Map<String, ArrayList<Track>> genreTracks;

    HomeContent(List<Track> recentlyPlayed, List<Track> recentlyAdded,
            List<Track> mostPlayed, List<Track> favorites, List<Track> allFavorites,
            List<Playlist> playlists,
            List<String> artists, List<String> albums,
            Map<String, ArrayList<Track>> folders,
            Map<String, ArrayList<Track>> artistTracks,
            Map<String, ArrayList<Track>> albumTracks,
            Map<String, ArrayList<Track>> genreTracks) {
        this.recentlyPlayed = immutable(recentlyPlayed);
        this.recentlyAdded = immutable(recentlyAdded);
        this.mostPlayed = immutable(mostPlayed);
        this.favorites = immutable(favorites);
        this.allFavorites = immutable(allFavorites);
        this.playlists = Collections.unmodifiableList(new ArrayList<>(playlists));
        this.artists = immutable(artists);
        this.albums = immutable(albums);
        this.folders = immutableGroups(folders);
        this.artistTracks = immutableGroups(artistTracks);
        this.albumTracks = immutableGroups(albumTracks);
        this.genreTracks = immutableGroups(genreTracks);
    }

    static HomeContent empty() {
        return new HomeContent(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap());
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static Map<String, ArrayList<Track>> immutableGroups(
            Map<String, ArrayList<Track>> source) {
        LinkedHashMap<String, ArrayList<Track>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ArrayList<Track>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
