package com.dumuzeyn.mp3player;

import android.content.Context;
import android.os.Handler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Loads an immutable library snapshot without delaying the Activity's first frame. */
final class LibraryLoader implements AutoCloseable {
    interface Callback {
        void loaded(Snapshot snapshot);
    }

    interface HomeCallback {
        void loaded(HomeContent content);
    }

    static final class Snapshot {
        final ArrayList<Track> tracks;
        final HashSet<String> favorites;
        final ArrayList<Playlist> playlists;
        final HomeContent homeContent;
        final long contentVersion;

        Snapshot(ArrayList<Track> tracks, HashSet<String> favorites,
                ArrayList<Playlist> playlists, long contentVersion) {
            this.tracks = new ArrayList<>(tracks);
            this.favorites = new HashSet<>(favorites);
            this.playlists = new ArrayList<>(playlists);
            this.homeContent = new HomeContentBuilder().build(
                    this.tracks, this.favorites, this.playlists);
            this.contentVersion = contentVersion;
        }
    }


    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    LibraryLoader(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
    }

    void load(int benchmarkTrackCount, Callback callback) {
        try {
            executor.execute(() -> {
                Snapshot snapshot = readSnapshot(benchmarkTrackCount);
                mainHandler.post(() -> {
                    if (!closed) {
                        callback.loaded(snapshot);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    void refreshHome(java.util.Set<String> favorites, java.util.List<Playlist> playlists,
            HomeCallback callback) {
        HashSet<String> favoriteSnapshot = new HashSet<>(favorites);
        ArrayList<Playlist> playlistSnapshot = new ArrayList<>(playlists);
        try {
            executor.execute(() -> {
                LibraryDatabase database = new LibraryDatabase(context.getApplicationContext());
                HomeContent content;
                try {
                    content = new HomeContentBuilder().build(database.loadTracks(),
                            favoriteSnapshot, playlistSnapshot);
                } finally {
                    database.close();
                }
                mainHandler.post(() -> {
                    if (!closed) {
                        callback.loaded(content);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    void deriveHome(java.util.List<Track> tracks, java.util.Set<String> favorites,
            java.util.List<Playlist> playlists, HomeCallback callback) {
        ArrayList<Track> trackSnapshot = new ArrayList<>(tracks);
        HashSet<String> favoriteSnapshot = new HashSet<>(favorites);
        ArrayList<Playlist> playlistSnapshot = new ArrayList<>(playlists);
        try {
            executor.execute(() -> {
                HomeContent content = new HomeContentBuilder().build(
                        trackSnapshot, favoriteSnapshot, playlistSnapshot);
                mainHandler.post(() -> {
                    if (!closed) {
                        callback.loaded(content);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }

    private Snapshot readSnapshot(int benchmarkTrackCount) {
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            LibraryDatabase.migrateLegacyIfNeeded(appContext);
            BenchmarkLibrarySeeder.seedIfRequested(appContext, benchmarkTrackCount);
            LibraryDatabase database = new LibraryDatabase(appContext);
            try {
                return new Snapshot(database.loadTracks(), database.loadFavorites(),
                        database.loadPlaylists(), LibraryContentVersion.read(appContext));
            } finally {
                database.close();
            }
        } catch (RuntimeException error) {
            VoltuneLog.failure("library_load_failed", error);
            return new Snapshot(new ArrayList<>(), new HashSet<>(), new ArrayList<>(),
                    LibraryContentVersion.read(context));
        }
    }
}
