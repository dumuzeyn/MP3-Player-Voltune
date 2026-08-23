package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class GlobalSearchResult {
    final List<Track> songs;
    final List<String> artists;
    final List<String> albums;
    final List<String> genres;
    final List<Playlist> playlists;

    GlobalSearchResult(List<Track> songs, List<String> artists, List<String> albums,
            List<String> genres, List<Playlist> playlists) {
        this.songs = immutable(songs);
        this.artists = immutable(artists);
        this.albums = immutable(albums);
        this.genres = immutable(genres);
        this.playlists = Collections.unmodifiableList(new ArrayList<>(playlists));
    }

    static GlobalSearchResult empty() {
        return new GlobalSearchResult(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
