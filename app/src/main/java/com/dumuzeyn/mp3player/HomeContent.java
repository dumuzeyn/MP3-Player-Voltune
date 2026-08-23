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
    final List<Playlist> playlists;
    final List<String> artists;
    final List<String> albums;
    final Map<String, ArrayList<Track>> folders;

    HomeContent(List<Track> recentlyPlayed, List<Track> recentlyAdded,
            List<Track> mostPlayed, List<Track> favorites, List<Playlist> playlists,
            List<String> artists, List<String> albums,
            Map<String, ArrayList<Track>> folders) {
        this.recentlyPlayed = immutable(recentlyPlayed);
        this.recentlyAdded = immutable(recentlyAdded);
        this.mostPlayed = immutable(mostPlayed);
        this.favorites = immutable(favorites);
        this.playlists = Collections.unmodifiableList(new ArrayList<>(playlists));
        this.artists = immutable(artists);
        this.albums = immutable(albums);
        this.folders = Collections.unmodifiableMap(new LinkedHashMap<>(folders));
    }

    static HomeContent empty() {
        return new HomeContent(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
