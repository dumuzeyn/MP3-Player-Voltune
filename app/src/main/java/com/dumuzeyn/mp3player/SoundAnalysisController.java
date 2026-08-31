package com.dumuzeyn.mp3player;

import android.content.Context;
import android.os.Process;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs one resumable, local-only analysis queue for the current process. */
final class SoundAnalysisController implements Closeable {
    private static final String PREFS = "mp3_player_ui";
    private static final String ENABLED = "soundAnalysisEnabled";
    private static final String CLUSTERING_VERSION = "similarClusteringVersion";
    private static final long CONSTRAINT_RECHECK_MS = 5_000L;
    private final MainActivityCore host;
    private SoundProfileStore store;
    private AudioFeatureExtractor extractor;
    private final SoundClusterEngine clusterEngine = new SoundClusterEngine();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voltune-sound-analysis");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private volatile ArrayList<SoundGroup> groups = new ArrayList<>();
    private volatile int total;
    private volatile int analyzed;
    private volatile int failed;
    private volatile int queued;
    private volatile String activeTitle = "";
    private volatile SoundAnalysisConstraints.BlockReason blockReason =
            SoundAnalysisConstraints.BlockReason.NONE;
    private volatile boolean closed;
    private volatile long lastLibrarySignature = Long.MIN_VALUE;
    private boolean updatePosted;

    SoundAnalysisController(MainActivityCore host) {
        this.host = host;
    }

    boolean enabled() {
        return host.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, true);
    }

    void toggle() {
        boolean value = !enabled();
        host.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED, value).apply();
        if (value) {
            lastLibrarySignature = Long.MIN_VALUE;
            onLibraryReady(host.libraryState.tracks);
        } else {
            generation.incrementAndGet();
            activeTitle = "";
            blockReason = SoundAnalysisConstraints.BlockReason.NONE;
            notifyUi();
        }
    }

    String settingLabel() {
        return host.tr("Analyze songs by sound: ", "Анализировать песни по звучанию: ")
                + host.tr(enabled() ? "on" : "off", enabled() ? "вкл" : "выкл");
    }

    void onLibraryReady(List<Track> tracks) {
        if (closed) {
            return;
        }
        long signature = librarySignature(tracks);
        if (signature == lastLibrarySignature) {
            return;
        }
        lastLibrarySignature = signature;
        int requestedGeneration = generation.incrementAndGet();
        ArrayList<Track> snapshot = new ArrayList<>(tracks);
        executor.execute(() -> runQueue(requestedGeneration, snapshot));
    }

    ArrayList<SoundGroup> groups() {
        return new ArrayList<>(groups);
    }

    int total() {
        return total;
    }

    int analyzed() {
        return analyzed;
    }

    int failed() {
        return failed;
    }

    int queued() {
        return queued;
    }

    String activeTitle() {
        return activeTitle;
    }

    SoundAnalysisConstraints.BlockReason blockReason() {
        return blockReason;
    }

    @Override
    public void close() {
        closed = true;
        generation.incrementAndGet();
        executor.shutdownNow();
        if (store != null) {
            store.close();
        }
    }

    private void runQueue(int requestedGeneration, ArrayList<Track> tracks) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        if (!isCurrent(requestedGeneration)) {
            return;
        }
        if (store == null) {
            store = new SoundProfileStore(host);
            extractor = new AudioFeatureExtractor(host);
        }
        LinkedHashMap<String, TrackAudioProfile> profiles = store.loadProfiles();
        groups = store.loadGroups();
        ArrayList<Track> pending = prepareQueue(tracks, profiles);
        int pendingCount = pending.size();
        boolean clusteringChanged = host.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(CLUSTERING_VERSION, 0) != SoundClusterEngine.CLUSTERING_VERSION;
        store.pruneEmptyGroups();
        groups = store.loadGroups();
        publishCounts(tracks.size(), profiles, pending.size());
        notifyUi();
        if (!enabled()) {
            return;
        }
        if (clusteringChanged && pending.isEmpty() && usableCount(profiles) >= 4) {
            rebuildGroups(profiles);
        }
        for (Track track : pending) {
            if (!runAllowedOrRescheduled(requestedGeneration, tracks)) {
                return;
            }
            activeTitle = track.title;
            store.mark(track, SoundAnalysisState.ANALYZING, "");
            notifyUi();
            try {
                double[] features = extractor.analyze(track,
                        () -> shouldYield(requestedGeneration));
                if (!isCurrent(requestedGeneration)) {
                    return;
                }
                TrackAudioProfile profile = TrackAudioProfile.analyzed(track, features);
                store.saveProfile(profile);
                profiles.put(track.trackId, profile);
                analyzed++;
                queued = Math.max(0, queued - 1);
                updateGroupsAfterProfile(profiles, profile);
            } catch (AudioFeatureExtractor.AnalysisInterruptedException interrupted) {
                if (!isCurrent(requestedGeneration)) {
                    return;
                }
                store.mark(track, SoundAnalysisState.QUEUED, "");
                lastLibrarySignature = Long.MIN_VALUE;
                onLibraryReady(tracks);
                return;
            } catch (Exception error) {
                if (!isCurrent(requestedGeneration)) {
                    return;
                }
                store.mark(track, SoundAnalysisState.FAILED,
                        error.getClass().getSimpleName());
                profiles.put(track.trackId, new TrackAudioProfile(track.trackId,
                        TrackAudioProfile.ANALYSIS_VERSION, track.fileSize, track.lastModified,
                        track.fingerprint, SoundAnalysisState.FAILED, new double[0], "",
                        error.getClass().getSimpleName(), System.currentTimeMillis()));
                failed++;
                queued = Math.max(0, queued - 1);
                VoltuneLog.failure("sound_analysis_failed", error);
            }
            activeTitle = "";
            notifyUi();
        }
        if (pendingCount > 0 || clusteringChanged || groups.isEmpty()) {
            rebuildGroups(profiles);
        }
        activeTitle = "";
        blockReason = SoundAnalysisConstraints.BlockReason.NONE;
        notifyUi();
    }

    private ArrayList<Track> prepareQueue(ArrayList<Track> tracks,
            LinkedHashMap<String, TrackAudioProfile> profiles) {
        ArrayList<Track> pending = new ArrayList<>();
        for (Track track : tracks) {
            TrackAudioProfile profile = profiles.get(track.trackId);
            if (profile != null && profile.matches(track)
                    && (profile.usable() || profile.state == SoundAnalysisState.FAILED)) {
                continue;
            }
            store.mark(track, SoundAnalysisState.QUEUED, "");
            TrackAudioProfile queuedProfile = TrackAudioProfile.pending(
                    track, SoundAnalysisState.QUEUED);
            profiles.put(track.trackId, queuedProfile);
            pending.add(track);
        }
        return pending;
    }

    private void updateGroupsAfterProfile(Map<String, TrackAudioProfile> profiles,
            TrackAudioProfile profile) {
        if (groups.isEmpty() && usableCount(profiles) >= 4) {
            rebuildGroups(profiles);
            return;
        }
        if (!groups.isEmpty()) {
            String groupId = clusterEngine.nearestGroup(profile.features,
                    new ArrayList<>(profiles.values()), groups);
            store.assign(profile.trackId, groupId);
            groups = store.loadGroups();
        }
        if (usableCount(profiles) <= 20) {
            rebuildGroups(profiles);
        }
    }

    private void rebuildGroups(Map<String, TrackAudioProfile> profiles) {
        ArrayList<SoundGroup> rebuilt = clusterEngine.cluster(
                new ArrayList<>(profiles.values()));
        store.replaceGroups(rebuilt);
        groups = store.loadGroups();
        host.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(CLUSTERING_VERSION, SoundClusterEngine.CLUSTERING_VERSION).apply();
    }

    private boolean runAllowedOrRescheduled(int requestedGeneration,
            ArrayList<Track> tracks) {
        if (!isCurrent(requestedGeneration)) return false;
        blockReason = SoundAnalysisConstraints.reason(host, host.isPlaybackPlaying());
        if (blockReason == SoundAnalysisConstraints.BlockReason.NONE) return true;
        activeTitle = "";
        notifyUi();
        host.uiHandler.postDelayed(() -> {
            if (!closed && enabled() && requestedGeneration == generation.get()) {
                lastLibrarySignature = Long.MIN_VALUE;
                onLibraryReady(tracks);
            }
        }, CONSTRAINT_RECHECK_MS);
        return false;
    }

    private boolean shouldYield(int requestedGeneration) {
        return !isCurrent(requestedGeneration)
                || SoundAnalysisConstraints.reason(host, host.isPlaybackPlaying())
                != SoundAnalysisConstraints.BlockReason.NONE;
    }

    private boolean isCurrent(int requestedGeneration) {
        return !closed && enabled() && requestedGeneration == generation.get()
                && !Thread.currentThread().isInterrupted();
    }

    private void publishCounts(int trackCount, Map<String, TrackAudioProfile> profiles,
            int pendingCount) {
        total = trackCount;
        analyzed = 0;
        failed = 0;
        for (TrackAudioProfile profile : profiles.values()) {
            if (profile.usable()) {
                analyzed++;
            } else if (profile.state == SoundAnalysisState.FAILED) {
                failed++;
            }
        }
        queued = pendingCount;
    }

    private static int usableCount(Map<String, TrackAudioProfile> profiles) {
        int result = 0;
        for (TrackAudioProfile profile : profiles.values()) {
            if (profile.usable()) {
                result++;
            }
        }
        return result;
    }

    private static long librarySignature(List<Track> tracks) {
        long value = 0xcbf29ce484222325L;
        for (Track track : tracks) {
            value = (value ^ track.trackId.hashCode()) * 0x100000001b3L;
            value = (value ^ track.fileSize) * 0x100000001b3L;
            value = (value ^ track.lastModified) * 0x100000001b3L;
            value = (value ^ track.fingerprint.hashCode()) * 0x100000001b3L;
        }
        return value;
    }

    private void notifyUi() {
        synchronized (this) {
            if (updatePosted || closed) {
                return;
            }
            updatePosted = true;
        }
        host.uiHandler.postDelayed(() -> {
            synchronized (SoundAnalysisController.this) {
                updatePosted = false;
            }
            if (!host.isFinishing()
                    && host.navigationState.tabIndex == LibraryTabs.SOUND) {
                host.render();
            }
            host.refreshSettingsLabels();
        }, 200L);
    }
}
