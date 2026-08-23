package com.dumuzeyn.mp3player;

import android.os.Bundle;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;
import androidx.media3.session.SessionError;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/** Media3 browse tree shared by Android Auto and other external media browsers. */
@UnstableApi
final class VoltuneMediaLibraryCallback implements MediaLibrarySession.Callback, AutoCloseable {
    interface CommandDelegate {
        ListenableFuture<SessionResult> handle(SessionCommand command, Bundle args);
        void onCommand(String action);
    }

    private static final String ROOT = "voltune.root";
    private static final String SONGS = "voltune.songs";
    private static final String ARTISTS = "voltune.artists";
    private static final String ALBUMS = "voltune.albums";
    private static final String PLAYLISTS = "voltune.playlists";
    private static final String SMART = "voltune.smart";
    private static final String ARTIST_PREFIX = "voltune.artist.";
    private static final String ALBUM_PREFIX = "voltune.album.";
    private static final String PLAYLIST_PREFIX = "voltune.playlist.";
    private static final String SMART_PREFIX = "voltune.smart.";

    private final LibraryDatabase database;
    private final MediaItemMapper mapper;
    private final CommandDelegate commands;
    private final SmartPlaylistResolver smartResolver = new SmartPlaylistResolver();
    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "media-library");
                thread.setDaemon(true);
                return thread;
            }));

    VoltuneMediaLibraryCallback(LibraryDatabase database, MediaItemMapper mapper,
            CommandDelegate commands) {
        this.database = database;
        this.mapper = mapper;
        this.commands = commands;
    }

    @Override
    public MediaSession.ConnectionResult onConnect(MediaSession session,
            MediaSession.ControllerInfo controller) {
        SessionCommands available = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(Media3Commands.TIMER_START_COMMAND)
                .add(Media3Commands.TIMER_CANCEL_COMMAND)
                .add(Media3Commands.AUDIO_EFFECTS_COMMAND)
                .add(Media3Commands.CLEAR_QUEUE_COMMAND)
                .add(Media3Commands.DIAGNOSTIC_SNAPSHOT_COMMAND)
                .build();
        return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available)
                .build();
    }

    @Override
    public ListenableFuture<SessionResult> onCustomCommand(MediaSession session,
            MediaSession.ControllerInfo controller, SessionCommand command, Bundle args) {
        String action = command.customAction == null ? "unknown" : command.customAction;
        commands.onCommand(action);
        return commands.handle(command, args);
    }

    @Override
    public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
            MediaLibrarySession session, MediaSession.ControllerInfo browser,
            @Nullable LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofItem(
                folder(ROOT, "Voltune", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED), params));
    }

    @Override
    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
            MediaLibrarySession session, MediaSession.ControllerInfo browser, String parentId,
            int page, int pageSize, @Nullable LibraryParams params) {
        if (page < 0 || pageSize < 1) {
            return Futures.immediateFuture(LibraryResult.ofError(
                    SessionError.ERROR_BAD_VALUE, params));
        }
        return executor.submit(() -> {
            List<MediaItem> children = children(parentId);
            return children == null
                    ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params)
                    : LibraryResult.ofItemList(page(children, page, pageSize), params);
        });
    }

    @Override
    public ListenableFuture<LibraryResult<MediaItem>> onGetItem(MediaLibrarySession session,
            MediaSession.ControllerInfo browser, String mediaId) {
        return executor.submit(() -> {
            MediaItem item = findItem(mediaId);
            return item == null ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    : LibraryResult.ofItem(item, null);
        });
    }

    @Override
    public ListenableFuture<LibraryResult<Void>> onSearch(MediaLibrarySession session,
            MediaSession.ControllerInfo browser, String query, @Nullable LibraryParams params) {
        return executor.submit(() -> {
            int count = search(query).size();
            session.notifySearchResultChanged(browser, query, count, params);
            return LibraryResult.ofVoid(params);
        });
    }

    @Override
    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
            MediaLibrarySession session, MediaSession.ControllerInfo browser, String query,
            int page, int pageSize, @Nullable LibraryParams params) {
        if (page < 0 || pageSize < 1) {
            return Futures.immediateFuture(LibraryResult.ofError(
                    SessionError.ERROR_BAD_VALUE, params));
        }
        return executor.submit(() -> LibraryResult.ofItemList(
                page(search(query), page, pageSize), params));
    }

    @Override
    public void close() {
        executor.shutdownNow();
        database.close();
    }

    @Nullable
    private List<MediaItem> children(String parentId) {
        if (ROOT.equals(parentId)) {
            ArrayList<MediaItem> result = new ArrayList<>();
            result.add(folder(SONGS, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED));
            result.add(folder(ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS));
            result.add(folder(ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS));
            result.add(folder(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS));
            result.add(folder(SMART, "Smart playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS));
            return result;
        }
        List<Track> tracks = database.loadTracks();
        if (SONGS.equals(parentId)) {
            return mediaItems(tracks);
        }
        if (ARTISTS.equals(parentId)) {
            return folders(uniqueValues(tracks, true), ARTIST_PREFIX,
                    MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS);
        }
        if (ALBUMS.equals(parentId)) {
            return folders(uniqueValues(tracks, false), ALBUM_PREFIX,
                    MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS);
        }
        if (PLAYLISTS.equals(parentId)) {
            ArrayList<MediaItem> result = new ArrayList<>();
            for (Playlist playlist : database.loadPlaylists()) {
                result.add(folder(PLAYLIST_PREFIX + encode(playlist.name), playlist.name,
                        MediaMetadata.MEDIA_TYPE_PLAYLIST));
            }
            return result;
        }
        if (SMART.equals(parentId)) {
            ArrayList<MediaItem> result = new ArrayList<>();
            for (SmartPlaylistDefinition definition : SmartPlaylistDefinition.values()) {
                result.add(folder(SMART_PREFIX + definition.name(), definition.englishName,
                        MediaMetadata.MEDIA_TYPE_PLAYLIST));
            }
            return result;
        }
        if (parentId.startsWith(ARTIST_PREFIX)) {
            return mediaItems(filter(tracks, decode(parentId.substring(ARTIST_PREFIX.length())),
                    true));
        }
        if (parentId.startsWith(ALBUM_PREFIX)) {
            return mediaItems(filter(tracks, decode(parentId.substring(ALBUM_PREFIX.length())),
                    false));
        }
        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            return playlistItems(tracks,
                    decode(parentId.substring(PLAYLIST_PREFIX.length())));
        }
        if (parentId.startsWith(SMART_PREFIX)) {
            return smartItems(tracks, parentId.substring(SMART_PREFIX.length()));
        }
        return null;
    }

    @Nullable
    private MediaItem findItem(String mediaId) {
        if (ROOT.equals(mediaId)) {
            return folder(ROOT, "Voltune", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
        }
        List<MediaItem> rootItems = children(ROOT);
        if (rootItems != null) {
            for (MediaItem item : rootItems) {
                if (mediaId.equals(item.mediaId)) {
                    return item;
                }
            }
        }
        for (Track track : database.loadTracks()) {
            if (mapper.matchesMediaId(track, mediaId)) {
                return mapper.toMediaItem(track);
            }
        }
        for (String category : new String[]{ARTISTS, ALBUMS, PLAYLISTS, SMART}) {
            List<MediaItem> items = children(category);
            if (items != null) {
                for (MediaItem item : items) {
                    if (mediaId.equals(item.mediaId)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    private List<MediaItem> search(String rawQuery) {
        String query = Track.normalizeSearchText(rawQuery);
        if (query.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<MediaItem> result = new ArrayList<>();
        for (Track track : database.loadTracks()) {
            if (track.normalizedSearchText.contains(query)) {
                result.add(mapper.toMediaItem(track));
            }
        }
        return result;
    }

    private List<MediaItem> smartItems(List<Track> tracks, String name) {
        try {
            SmartPlaylistDefinition definition = SmartPlaylistDefinition.valueOf(name);
            return mediaItems(smartResolver.resolve(definition, tracks,
                    database.loadFavorites(), System.currentTimeMillis(), 0));
        } catch (IllegalArgumentException error) {
            return Collections.emptyList();
        }
    }

    private List<MediaItem> playlistItems(List<Track> tracks, String name) {
        for (Playlist playlist : database.loadPlaylists()) {
            if (playlist.name.equals(name)) {
                ArrayList<Track> selected = new ArrayList<>();
                for (String uri : playlist.uris) {
                    for (Track track : tracks) {
                        if (uri.equals(track.uri)) {
                            selected.add(track);
                            break;
                        }
                    }
                }
                return mediaItems(selected);
            }
        }
        return Collections.emptyList();
    }

    private List<MediaItem> mediaItems(List<Track> tracks) {
        ArrayList<MediaItem> result = new ArrayList<>();
        for (Track track : tracks) {
            result.add(mapper.toMediaItem(track));
        }
        return result;
    }

    private static List<Track> filter(List<Track> tracks, String value, boolean artist) {
        ArrayList<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (value.equals(artist ? track.artist : track.album)) {
                result.add(track);
            }
        }
        return result;
    }

    private static Set<String> uniqueValues(List<Track> tracks, boolean artist) {
        ArrayList<String> values = new ArrayList<>();
        for (Track track : tracks) {
            String value = artist ? track.artist : track.album;
            if (value != null && !value.trim().isEmpty()) {
                values.add(value);
            }
        }
        java.util.Collections.sort(values, String.CASE_INSENSITIVE_ORDER);
        return new LinkedHashSet<>(values);
    }

    private static List<MediaItem> folders(Set<String> values, String prefix, int mediaType) {
        ArrayList<MediaItem> result = new ArrayList<>();
        for (String value : values) {
            result.add(folder(prefix + encode(value), value, mediaType));
        }
        return result;
    }

    private static MediaItem folder(String id, String title, int mediaType) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .build();
        return new MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build();
    }

    private static List<MediaItem> page(List<MediaItem> source, int page, int pageSize) {
        long startLong = (long) page * pageSize;
        if (startLong >= source.size()) {
            return Collections.emptyList();
        }
        int start = (int) startLong;
        return new ArrayList<>(source.subList(start, Math.min(source.size(), start + pageSize)));
    }

    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String decode(String value) {
        try {
            return new String(Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return "";
        }
    }
}
