package com.dumuzeyn.mp3player;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Persists collection edits without blocking taps, scrolling, or overlay animations. */
final class LibraryPersistenceController {
    private static final String TAG = "VoltuneDebug";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private Snapshot pending;
    private boolean workerScheduled;

    LibraryPersistenceController(Context context) {
        this.context = context;
    }

    void save(Set<String> favorites, List<Playlist> playlists) {
        synchronized (lock) {
            pending = new Snapshot(favorites, playlists);
            if (workerScheduled) {
                return;
            }
            workerScheduled = true;
        }
        try {
            executor.execute(this::drain);
        } catch (RejectedExecutionException ignored) {
            synchronized (lock) {
                workerScheduled = false;
            }
        }
    }

    void close() {
        executor.shutdown();
    }

    private void drain() {
        while (true) {
            Snapshot snapshot;
            synchronized (lock) {
                snapshot = pending;
                pending = null;
                if (snapshot == null) {
                    workerScheduled = false;
                    return;
                }
            }
            LibraryDatabase database = new LibraryDatabase(context);
            try {
                database.saveCollections(snapshot.favorites, snapshot.playlists);
            } catch (RuntimeException error) {
                Log.e(TAG, "collection_save_failed error=" + error.getMessage(), error);
            } finally {
                database.close();
            }
        }
    }

    private static final class Snapshot {
        final HashSet<String> favorites;
        final ArrayList<Playlist> playlists;

        Snapshot(Set<String> favorites, List<Playlist> playlists) {
            this.favorites = new HashSet<>(favorites);
            this.playlists = new ArrayList<>();
            for (Playlist source : playlists) {
                Playlist copy = new Playlist(source.name);
                copy.uris.addAll(source.uris);
                this.playlists.add(copy);
            }
        }
    }
}
