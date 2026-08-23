package com.dumuzeyn.mp3player;

import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Debounced cancellable global search with stale-result protection. */
final class GlobalSearchController implements AutoCloseable {
    interface Callback {
        void completed(GlobalSearchResult result);
    }

    private static final long DEBOUNCE_MS = 220L;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final GlobalSearchEngine engine = new GlobalSearchEngine();
    private Runnable scheduled;
    private int generation;
    private boolean closed;

    GlobalSearchController(Handler mainHandler) {
        this.mainHandler = mainHandler;
    }

    synchronized void search(List<Track> tracks, List<Playlist> playlists, String query,
            Callback callback) {
        if (closed) {
            return;
        }
        int request = ++generation;
        if (scheduled != null) {
            mainHandler.removeCallbacks(scheduled);
        }
        ArrayList<Track> trackSnapshot = new ArrayList<>(tracks);
        ArrayList<Playlist> playlistSnapshot = new ArrayList<>(playlists);
        scheduled = () -> execute(request, trackSnapshot, playlistSnapshot, query, callback);
        mainHandler.postDelayed(scheduled, DEBOUNCE_MS);
    }

    synchronized void cancel() {
        generation++;
        if (scheduled != null) {
            mainHandler.removeCallbacks(scheduled);
            scheduled = null;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        cancel();
        executor.shutdownNow();
    }

    private void execute(int request, List<Track> tracks, List<Playlist> playlists,
            String query, Callback callback) {
        synchronized (this) {
            scheduled = null;
            if (closed || request != generation) {
                return;
            }
        }
        try {
            executor.execute(() -> deliver(request,
                    engine.search(tracks, playlists, query, 20), callback));
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    private void deliver(int request, GlobalSearchResult result, Callback callback) {
        mainHandler.post(() -> {
            synchronized (GlobalSearchController.this) {
                if (closed || request != generation) {
                    return;
                }
            }
            callback.completed(result);
        });
    }
}
