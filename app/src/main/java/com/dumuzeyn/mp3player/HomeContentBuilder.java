package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HomeContentBuilder {
    private static final int SECTION_LIMIT = 8;
    private final SmartPlaylistResolver smartResolver = new SmartPlaylistResolver();

    HomeContent build(List<Track> tracks, Set<String> favorites, List<Playlist> playlists) {
        long now = System.currentTimeMillis();
        ArrayList<Track> favoriteTracks = new ArrayList<>();
        for (Track track : tracks) {
            if (favorites.contains(track.uri)) {
                favoriteTracks.add(track);
            }
        }
        java.util.Collections.sort(favoriteTracks,
                (left, right) -> Integer.compare(right.playCount, left.playCount));
        return new HomeContent(
                smart(SmartPlaylistDefinition.RECENTLY_PLAYED, tracks, favorites, now),
                smart(SmartPlaylistDefinition.RECENTLY_ADDED, tracks, favorites, now),
                smart(SmartPlaylistDefinition.MOST_PLAYED, tracks, favorites, now),
                limited(favoriteTracks), recentPlaylists(playlists),
                popularGroups(tracks, true), popularGroups(tracks, false),
                new FolderGrouping().group(tracks));
    }

    private List<Track> smart(SmartPlaylistDefinition definition, List<Track> tracks,
            Set<String> favorites, long now) {
        return smartResolver.resolve(definition, tracks, favorites, now, SECTION_LIMIT);
    }

    private List<Track> limited(List<Track> source) {
        return source.size() <= SECTION_LIMIT ? source
                : new ArrayList<>(source.subList(0, SECTION_LIMIT));
    }

    private List<Playlist> recentPlaylists(List<Playlist> source) {
        ArrayList<Playlist> result = new ArrayList<>();
        for (int index = source.size() - 1; index >= 0 && result.size() < SECTION_LIMIT;
                index--) {
            result.add(source.get(index));
        }
        return result;
    }

    private List<String> popularGroups(List<Track> tracks, boolean artist) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Track track : tracks) {
            String value = artist ? track.artist : track.album;
            counts.put(value, counts.containsKey(value) ? counts.get(value) + 1 : 1);
        }
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        java.util.Collections.sort(entries,
                (left, right) -> Integer.compare(right.getValue(), left.getValue()));
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) {
            if (result.size() == SECTION_LIMIT) {
                break;
            }
            result.add(entry.getKey());
        }
        return result;
    }
}
