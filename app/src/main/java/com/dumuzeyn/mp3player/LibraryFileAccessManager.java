package com.dumuzeyn.mp3player;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class LibraryFileAccessManager {
    private LibraryFileAccessManager() {
    }

    static Result inspect(Context context, List<Track> tracks) {
        ArrayList<Track> unavailable = new ArrayList<>();
        for (Track track : tracks) {
            if (TrackStore.canOpenForRead(context, track.asUri())) {
                TrackStore.updateAvailability(context, track.trackId, "");
            } else {
                TrackStore.updateAvailability(context, track.trackId,
                        "File or persisted SAF permission is unavailable");
                unavailable.add(track);
            }
        }
        return new Result(tracks.size() - unavailable.size(), unavailable);
    }

    static void removeUnavailable(Context context, List<Track> tracks, Set<String> favorites,
            List<Playlist> playlists) {
        HashSet<String> removedUris = new HashSet<>();
        Iterator<Track> iterator = tracks.iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            if (!TrackStore.canOpenForRead(context, track.asUri())) {
                removedUris.add(track.uri);
                iterator.remove();
            }
        }
        favorites.removeAll(removedUris);
        for (Playlist playlist : playlists) {
            playlist.uris.removeAll(removedUris);
        }
        TrackStore.save(context, tracks);
    }

    static final class Result {
        final int availableCount;
        final List<Track> unavailable;

        Result(int availableCount, List<Track> unavailable) {
            this.availableCount = availableCount;
            this.unavailable = unavailable;
        }
    }
}
