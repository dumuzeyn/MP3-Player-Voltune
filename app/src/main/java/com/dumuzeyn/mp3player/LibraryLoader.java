package com.dumuzeyn.mp3player;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
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

    static final class Snapshot {
        final ArrayList<Track> tracks;
        final HashSet<String> favorites;
        final ArrayList<Playlist> playlists;

        Snapshot(ArrayList<Track> tracks, HashSet<String> favorites,
                ArrayList<Playlist> playlists) {
            this.tracks = new ArrayList<>(tracks);
            this.favorites = new HashSet<>(favorites);
            this.playlists = new ArrayList<>(playlists);
        }
    }

    private static final String TAG = "VoltuneDebug";

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
                        database.loadPlaylists());
            } finally {
                database.close();
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "library_load_failed error=" + error.getMessage(), error);
            return new Snapshot(new ArrayList<>(), new HashSet<>(), new ArrayList<>());
        }
    }
}
