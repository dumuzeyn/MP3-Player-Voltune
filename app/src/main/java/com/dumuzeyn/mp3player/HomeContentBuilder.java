package com.dumuzeyn.mp3player;

import java.util.ArrayList;
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
        Groups groups = new Groups(tracks);
        return new HomeContent(
                smart(SmartPlaylistDefinition.RECENTLY_PLAYED, tracks, favorites, now),
                smart(SmartPlaylistDefinition.RECENTLY_ADDED, tracks, favorites, now),
                smart(SmartPlaylistDefinition.MOST_PLAYED, tracks, favorites, now),
                limited(favoriteTracks), favoriteTracks, recentPlaylists(playlists),
                popularGroups(groups.artists), popularGroups(groups.albums),
                new FolderGrouping().group(tracks), groups.artists, groups.albums,
                groups.genres);
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

    private List<String> popularGroups(Map<String, ArrayList<Track>> groups) {
        ArrayList<Map.Entry<String, ArrayList<Track>>> entries =
                new ArrayList<>(groups.entrySet());
        java.util.Collections.sort(entries,
                (left, right) -> Integer.compare(
                        right.getValue().size(), left.getValue().size()));
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Track>> entry : entries) {
            if (entry.getKey().isEmpty()) {
                continue;
            }
            if (result.size() == SECTION_LIMIT) {
                break;
            }
            result.add(entry.getKey());
        }
        return result;
    }

    private static final class Groups {
        final LinkedHashMap<String, ArrayList<Track>> artists = new LinkedHashMap<>();
        final LinkedHashMap<String, ArrayList<Track>> albums = new LinkedHashMap<>();
        final LinkedHashMap<String, ArrayList<Track>> genres = new LinkedHashMap<>();

        Groups(List<Track> tracks) {
            for (Track track : tracks) {
                add(artists, groupName(track.artist, "Unknown artist"), track);
                add(albums, groupName(track.album, "Unknown album"), track);
                add(genres, GenreNormalizer.isUnknown(track.genre) ? "" : track.genre.trim(),
                        track);
            }
        }

        private static String groupName(String value, String placeholder) {
            return value == null || value.trim().isEmpty()
                    || placeholder.equalsIgnoreCase(value.trim()) ? "" : value.trim();
        }

        private static void add(Map<String, ArrayList<Track>> target, String name, Track track) {
            ArrayList<Track> group = target.get(name);
            if (group == null) {
                group = new ArrayList<>();
                target.put(name, group);
            }
            group.add(track);
        }
    }
}
