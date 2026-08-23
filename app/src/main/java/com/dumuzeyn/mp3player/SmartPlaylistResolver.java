package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class SmartPlaylistResolver {
    private static final long STALE_AFTER_MS = 30L * 24L * 60L * 60L * 1000L;

    List<Track> resolve(SmartPlaylistDefinition definition, List<Track> library,
            Set<String> favorites, long now, int limit) {
        ArrayList<Track> result = new ArrayList<>();
        for (Track track : library) {
            if (matches(definition, track, favorites, now)) {
                result.add(track);
            }
        }
        java.util.Collections.sort(result, comparator(definition, favorites));
        if (limit > 0 && result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    private boolean matches(SmartPlaylistDefinition definition, Track track,
            Set<String> favorites, long now) {
        switch (definition) {
            case RECENTLY_PLAYED:
                return track.lastPlayedAt > 0L;
            case MOST_PLAYED:
                return track.playCount > 0;
            case RECENTLY_ADDED:
                return track.dateAdded > 0L;
            case NOT_PLAYED_RECENTLY:
                return track.playCount > 0 && track.lastPlayedAt < now - STALE_AFTER_MS;
            case NEVER_PLAYED:
                return track.playCount == 0;
            case MOST_LOVED:
                return favorites.contains(track.uri);
            default:
                return false;
        }
    }

    private Comparator<Track> comparator(SmartPlaylistDefinition definition,
            Set<String> favorites) {
        if (definition == SmartPlaylistDefinition.RECENTLY_ADDED) {
            return (left, right) -> Long.compare(right.dateAdded, left.dateAdded);
        }
        if (definition == SmartPlaylistDefinition.MOST_PLAYED
                || definition == SmartPlaylistDefinition.MOST_LOVED) {
            return (left, right) -> {
                int count = Integer.compare(right.playCount, left.playCount);
                return count != 0 ? count : Long.compare(right.lastPlayedAt, left.lastPlayedAt);
            };
        }
        return (left, right) -> {
            int played = Long.compare(right.lastPlayedAt, left.lastPlayedAt);
            return played != 0 ? played
                    : String.CASE_INSENSITIVE_ORDER.compare(left.title, right.title);
        };
    }
}
