package com.dumuzeyn.mp3player;

import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Owns library-facing queue decisions; ExoPlayer remains the active queue owner. */
final class PlaybackQueueController {
    private final MainActivityCore host;
    private final PlaybackController playback;
    private final LibraryMutationController mutations;

    PlaybackQueueController(MainActivityCore host, PlaybackController playback) {
        this.host = host;
        this.playback = playback;
        this.mutations = new LibraryMutationController(host);
    }

    void playTrack(Track track, boolean refreshList) {
        if (track == null || host.libraryState.tracks.indexOf(track) < 0) {
            return;
        }
        ArrayList<Track> queue = new ArrayList<>();
        queue.add(track);
        playback.submitQueue(queue, 0, 0, host.repeatMode(), true);
        if (refreshList) {
            host.refreshAfterTrackChange();
        }
    }

    void playList(ArrayList<Track> source, boolean shuffle) {
        if (source == null || source.isEmpty()) {
            return;
        }
        ArrayList<Track> queue = new ArrayList<>(source);
        if (shuffle) {
            Collections.shuffle(queue, new Random());
        }
        playback.submitQueue(queue, 0, 0, host.repeatMode(), true);
    }

    void toggleOrStart() {
        if (playback.hasPlaybackSession()) {
            playback.toggle();
        } else if (!host.libraryState.tracks.isEmpty()) {
            playList(host.libraryState.tracks, false);
        }
    }

    void restore(PlaybackStateManager.State state) {
        Track current = host.findTrack(state.uri);
        ArrayList<Track> queue = PlaybackQueueResolver.restore(
                host.libraryState.tracks, state.queueUris, current);
        if (!queue.isEmpty()) {
            playback.submitQueue(queue, Math.min(state.index, queue.size() - 1),
                    state.position, state.loopMode, state.playing);
        }
    }

    void clear() {
        new PlaybackStateManager(host).clear();
        playback.clearQueue();
    }

    void add(Track track) {
        if (track == null || containsUri(activeQueue(), track.uri)) {
            return;
        }
        playback.addQueueItem(track);
    }

    void addAll(List<Track> tracks) {
        ArrayList<Track> additions = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (Track queued : activeQueue()) {
            seen.add(queued.uri);
        }
        for (Track track : tracks) {
            if (track != null && seen.add(track.uri)) {
                additions.add(track);
            }
        }
        playback.addQueueItems(additions);
    }

    void playNext(Track track) {
        if (track != null) {
            playback.playNext(track);
        }
    }

    void move(int from, int to) {
        playback.moveQueueItem(from, to);
    }

    void remove(Track track) {
        if (track == null) {
            return;
        }
        int index = indexOfUri(activeQueue(), track.uri);
        if (index >= 0) {
            playback.removeQueueItem(index);
        }
    }

    void removeFromLibrary(Track track) {
        Track stored = track == null ? null : host.findTrack(track.uri);
        if (stored == null) {
            return;
        }
        mutations.removeTrack(stored);
    }

    void removeDeletedFile(Track track) {
        Track stored = track == null ? null : host.findTrack(track.uri);
        if (stored != null) {
            mutations.removeDeletedFile(stored);
        }
    }

    void removeSource(LibrarySource source) {
        mutations.removeSource(source);
    }

    void clearLibrary() {
        mutations.clearLibrary();
    }

    void close() {
        mutations.close();
    }

    void removeCommitted(java.util.Set<String> trackIds, java.util.Set<String> trackUris) {
        if (trackIds.isEmpty()) {
            return;
        }
        new PlaybackStateManager(host).removeTracks(trackIds, trackUris);
        for (int index = host.playbackUiState.queue.size() - 1; index >= 0; index--) {
            if (trackIds.contains(host.playbackUiState.queue.get(index).trackId)) {
                host.playbackUiState.queue.remove(index);
            }
        }
        playback.removeQueueItems(trackIds);
    }

    void playIndex(int index, int position) {
        ArrayList<Track> queue = new ArrayList<>(activeQueue());
        if (!queue.isEmpty()) {
            playback.submitQueue(queue, Math.max(0, Math.min(index, queue.size() - 1)),
                    position, host.repeatMode(), true);
        }
    }

    void seekIndex(int index) {
        playback.seekQueueItem(index);
    }

    String loopLabel() {
        if (host.repeatMode() == 1) {
            return host.tr("Song ↻", "Песня ↻");
        }
        if (host.repeatMode() == 2) {
            return host.tr("List ↻", "Список ↻");
        }
        return host.tr("Repeat ↻", "Повтор ↻");
    }

    ArrayList<Track> activeQueue() {
        return host.playbackUiState.queue.isEmpty() ? host.libraryState.tracks : host.playbackUiState.queue;
    }

    ArrayList<String> queueUris() {
        ArrayList<String> uris = new ArrayList<>();
        Iterator<Track> iterator = activeQueue().iterator();
        while (iterator.hasNext()) {
            uris.add(iterator.next().uri);
        }
        return uris;
    }

    boolean isPlayingSource(ArrayList<Track> source) {
        return host.isPlaybackPlaying() && source != null
                && sameOrderedQueue(source, host.playbackUiState.queue);
    }

    boolean isPlayingCollection(ArrayList<Track> source) {
        return host.isPlaybackPlaying() && isCurrentCollection(source);
    }

    boolean isCurrentCollection(ArrayList<Track> source) {
        if (source == null || source.isEmpty() || host.playbackUiState.queue.size() != source.size()) {
            return false;
        }
        HashSet<String> expected = new HashSet<>();
        HashSet<String> active = new HashSet<>();
        for (Track track : source) {
            expected.add(track.uri);
        }
        for (Track track : host.playbackUiState.queue) {
            active.add(track.uri);
        }
        return expected.size() == source.size() && expected.equals(active);
    }

    int indexOf(Track track) {
        int index = indexOfUri(activeQueue(), track.uri);
        return index >= 0 ? index : Math.max(0, host.libraryState.tracks.indexOf(track));
    }

    private static boolean sameOrderedQueue(ArrayList<Track> first, ArrayList<Track> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).uri.equals(second.get(index).uri)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsUri(ArrayList<Track> tracks, String uri) {
        return indexOfUri(tracks, uri) >= 0;
    }

    private static int indexOfUri(ArrayList<Track> tracks, String uri) {
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).uri.equals(uri)) {
                return index;
            }
        }
        return -1;
    }
}
