package com.dumuzeyn.mp3player;

import android.view.View;
import android.widget.Button;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;

final class PlaylistController {
    private final MainActivityCore host;
    private final ArrayList<PlaybackBinding> playbackBindings = new ArrayList<>();
    private int playbackGeneration = -1;

    PlaylistController(MainActivityCore host) {
        this.host = host;
    }

    void beginPlaybackBindings(int generation) {
        if (playbackGeneration == generation) {
            return;
        }
        playbackGeneration = generation;
        playbackBindings.clear();
    }

    void bindPlaybackState(Button playButton, View marker, ArrayList<Track> tracks, int generation) {
        beginPlaybackBindings(generation);
        PlaybackBinding binding = new PlaybackBinding(
                playButton, marker, new ArrayList<>(tracks), generation);
        playbackBindings.add(binding);
        binding.apply();
    }

    void refreshPlaybackState() {
        Iterator<PlaybackBinding> iterator = playbackBindings.iterator();
        while (iterator.hasNext()) {
            PlaybackBinding binding = iterator.next();
            if (!binding.isCurrentGeneration()) {
                iterator.remove();
                continue;
            }
            binding.apply();
        }
    }

    ArrayList<Playlist> filteredPlaylists(String query) {
        ArrayList<Playlist> result = new ArrayList<>();
        String normalized = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        for (Playlist playlist : host.libraryState.playlists) {
            if (normalized.isEmpty()
                    || host.containsSearch(playlist.name, normalized)
                    || playlistContainsSearch(playlist, normalized)) {
                result.add(playlist);
            }
        }
        return result;
    }

    ArrayList<Track> playlistTracks(Playlist playlist) {
        ArrayList<Track> result = new ArrayList<>();
        for (String uri : playlist.uris) {
            Track track = host.findTrack(uri);
            if (track != null) {
                result.add(track);
            }
        }
        return result;
    }

    ArrayList<Track> sortedPlaylistTracks(Playlist playlist) {
        ArrayList<Track> result = playlistTracks(playlist);
        Collections.sort(result, new Comparator<Track>() {
            @Override
            public int compare(Track left, Track right) {
                String leftTitle = left == null || left.title == null ? "" : left.title;
                String rightTitle = right == null || right.title == null ? "" : right.title;
                return leftTitle.compareToIgnoreCase(rightTitle);
            }
        });
        return result;
    }

    private final class PlaybackBinding {
        private final Button playButton;
        private final View marker;
        private final ArrayList<Track> tracks;
        private final int generation;

        PlaybackBinding(Button playButton, View marker, ArrayList<Track> tracks, int generation) {
            this.playButton = playButton;
            this.marker = marker;
            this.tracks = tracks;
            this.generation = generation;
        }

        boolean isCurrentGeneration() {
            return generation == playbackGeneration
                    && generation == host.navigationState.songRenderGeneration;
        }

        void apply() {
            marker.setVisibility(host.playbackQueueController.isCurrentCollection(tracks) ? View.VISIBLE : View.INVISIBLE);
            SongRowStateRegistry.applyPlayState(
                    playButton, host.playbackQueueController.isPlayingCollection(tracks));
        }
    }

    boolean playlistContainsSearch(Playlist playlist, String query) {
        for (Track track : playlistTracks(playlist)) {
            if (host.matchesTrackSearch(track, query)) {
                return true;
            }
        }
        return false;
    }

    void addTracksToPlaylist(Playlist playlist, Set<String> uris) {
        for (String uri : uris) {
            if (!playlist.uris.contains(uri)) {
                playlist.uris.add(uri);
            }
        }
        host.saveLibraryState();
        host.librarySnapshotApplier.rebuildDerivedAndRender();
    }

    void addTrackToPlaylist(Playlist playlist, Track track) {
        if (!playlist.uris.contains(track.uri)) {
            playlist.uris.add(track.uri);
        }
        host.saveLibraryState();
        host.librarySnapshotApplier.rebuildDerivedAndRender();
    }

    Playlist createPlaylist(String rawName) {
        String name = cleanPlaylistName(rawName);
        Playlist playlist = new Playlist(name);
        host.libraryState.playlists.add(playlist);
        host.saveLibraryState();
        host.librarySnapshotApplier.rebuildDerivedAndRender();
        return playlist;
    }

    Playlist createPlaylistWithTrack(String rawName, Track track) {
        Playlist playlist = createPlaylist(rawName);
        if (!playlist.uris.contains(track.uri)) {
            playlist.uris.add(track.uri);
            host.saveLibraryState();
            host.librarySnapshotApplier.rebuildDerivedAndRender();
        }
        return playlist;
    }

    void renamePlaylist(Playlist playlist, String rawName) {
        playlist.name = cleanPlaylistName(rawName);
        host.saveLibraryState();
        host.librarySnapshotApplier.rebuildDerivedAndRender();
    }

    void deletePlaylist(Playlist playlist) {
        host.libraryState.playlists.remove(playlist);
        host.saveLibraryState();
        host.librarySnapshotApplier.rebuildDerivedAndRender();
    }

    void removeTrackFromAllPlaylists(Track track) {
        for (Playlist playlist : host.libraryState.playlists) {
            playlist.uris.remove(track.uri);
        }
        host.saveLibraryState();
        host.librarySnapshotApplier.rebuildDerivedAndRender();
    }

    private String cleanPlaylistName(String rawName) {
        String name = PlaylistManager.cleanName(rawName);
        return name.isEmpty() ? host.tr("Playlist", "Плейлист") : name;
    }
}
