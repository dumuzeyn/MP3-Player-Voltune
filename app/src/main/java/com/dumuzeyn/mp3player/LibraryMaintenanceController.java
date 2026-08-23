package com.dumuzeyn.mp3player;

import android.content.Context;
import android.os.Handler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reconciles persisted tracks and repairs old metadata entirely off the UI thread. */
final class LibraryMaintenanceController implements AutoCloseable {
    static final int METADATA_REVISION = 1;

    interface Callback {
        void finished(List<Track> refreshed, List<Track> unavailable);
    }

    interface UnavailableCallback {
        void finished(List<Track> unavailable);
    }

    private Context context;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean closed;

    LibraryMaintenanceController(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
    }

    void run(List<Track> source, Callback callback) {
        if (closed || !started.compareAndSet(false, true)) {
            return;
        }
        promoteApplicationContext();
        ArrayList<Track> snapshot = new ArrayList<>(source);
        try {
            executor.execute(() -> maintain(snapshot, callback));
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    void inspectUnavailable(List<Track> source, UnavailableCallback callback) {
        promoteApplicationContext();
        ArrayList<Track> snapshot = new ArrayList<>(source);
        try {
            executor.execute(() -> {
                ArrayList<Track> unavailable = unavailable(snapshot);
                mainHandler.post(() -> {
                    if (!closed) {
                        callback.finished(unavailable);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    void removeUnavailable(List<Track> source, Runnable callback) {
        promoteApplicationContext();
        ArrayList<Track> unavailable = new ArrayList<>(source);
        try {
            executor.execute(() -> {
                TrackStore.applyMaintenance(context, new ArrayList<>(), new HashSet<>(),
                        unavailable, METADATA_REVISION);
                mainHandler.post(() -> {
                    if (!closed) {
                        callback.run();
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

    private void maintain(ArrayList<Track> tracks, Callback callback) {
        ArrayList<Track> refreshed = new ArrayList<>();
        ArrayList<Track> unavailable = new ArrayList<>();
        HashSet<String> checked = new HashSet<>();
        Map<String, Track> candidates = candidatesById();
        for (Track track : tracks) {
            if (closed) {
                return;
            }
            LibraryFileAccessManager.AccessState access =
                    LibraryFileAccessManager.accessState(context, track);
            if (access == LibraryFileAccessManager.AccessState.UNAVAILABLE) {
                unavailable.add(track);
                continue;
            }
            if (access != LibraryFileAccessManager.AccessState.AVAILABLE
                    || !candidates.containsKey(track.trackId)) {
                continue;
            }
            checked.add(track.trackId);
            if (!needsRefresh(track)) {
                continue;
            }
            Track updated = TrackStore.refreshMetadata(context, track);
            if (metadataChanged(track, updated)) {
                refreshed.add(updated);
            }
        }
        if (closed) {
            return;
        }
        TrackStore.applyMaintenance(context, refreshed, checked, unavailable,
                METADATA_REVISION);
        mainHandler.post(() -> {
            if (!closed) {
                callback.finished(refreshed, unavailable);
            }
        });
    }

    private ArrayList<Track> unavailable(List<Track> tracks) {
        ArrayList<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (closed) {
                break;
            }
            if (LibraryFileAccessManager.accessState(context, track)
                    == LibraryFileAccessManager.AccessState.UNAVAILABLE) {
                result.add(track);
            }
        }
        return result;
    }

    private Map<String, Track> candidatesById() {
        HashMap<String, Track> result = new HashMap<>();
        for (Track track : TrackStore.loadMetadataRefreshCandidates(
                context, METADATA_REVISION)) {
            result.put(track.trackId, track);
        }
        return result;
    }

    private void promoteApplicationContext() {
        Context application = context.getApplicationContext();
        if (application != null) {
            context = application;
        }
    }

    private static boolean needsRefresh(Track track) {
        return track.durationMs <= 0
                || isMissing(track.artist, "Unknown artist")
                || isMissing(track.album, "Unknown album")
                || GenreNormalizer.isUnknown(track.genre);
    }

    private static boolean isMissing(String value, String placeholder) {
        return value == null || value.trim().isEmpty() || placeholder.equalsIgnoreCase(value.trim())
                || "<unknown>".equalsIgnoreCase(value.trim());
    }

    private static boolean metadataChanged(Track before, Track after) {
        return before.durationMs != after.durationMs
                || !same(before.artist, after.artist)
                || !same(before.album, after.album)
                || !same(before.albumArtist, after.albumArtist)
                || !same(before.genre, after.genre)
                || before.year != after.year
                || before.trackNumber != after.trackNumber
                || before.discNumber != after.discNumber;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
