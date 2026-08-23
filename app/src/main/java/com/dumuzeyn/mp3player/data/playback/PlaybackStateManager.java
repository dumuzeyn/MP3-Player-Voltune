package com.dumuzeyn.mp3player.data.playback;

import android.content.Context;
import android.content.SharedPreferences;
import com.dumuzeyn.mp3player.MiniPlayerRetentionPolicy;
import com.dumuzeyn.mp3player.Track;
import com.dumuzeyn.mp3player.PlaybackSnapshot;
import com.dumuzeyn.mp3player.RepeatModeMapper;
import com.dumuzeyn.mp3player.QueueRemovalPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;

/** Persists the playback session independently from the service and UI. */
public final class PlaybackStateManager {
    public static final String PREFS = "player_resume";
    private static final String DURATION = "duration";
    private static final String INDEX = "index";
    private static final String INACTIVE_SINCE = "inactiveSince";
    private static final String LOOP_MODE = "loopMode";
    private static final String PLAYING = "playing";
    private static final String POSITION = "position";
    private static final String QUEUE = "queue";
    private static final String SAVED_AT = "savedAt";
    private static final String SHUFFLE = "shuffle";
    private static final String URI = "uri";

    private final SharedPreferences preferences;
    private String lastSavedQueueJson = "";

    public PlaybackStateManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public State load() {
        return new State(
                preferences.getString(URI, ""),
                Math.max(0, preferences.getInt(POSITION, 0)),
                Math.max(0, preferences.getInt(DURATION, 0)),
                Math.max(0, preferences.getInt(INDEX, 0)),
                preferences.getInt(LOOP_MODE, 0),
                preferences.getBoolean(PLAYING, false),
                preferences.getBoolean(SHUFFLE, false),
                preferences.getLong(SAVED_AT, 0L),
                preferences.getLong(INACTIVE_SINCE, 0L),
                queueUris(preferences.getString(QUEUE, "[]")));
    }

    public void save(Snapshot snapshot, boolean includeQueue) {
        String queueJson = queueJson(snapshot.queueUris);
        long now = System.currentTimeMillis();
        long inactiveSince = snapshot.playing
                ? 0L
                : preferences.getLong(INACTIVE_SINCE, 0L);
        if (!snapshot.playing && inactiveSince <= 0L) {
            inactiveSince = now;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(URI, snapshot.uri)
                .putInt(POSITION, Math.max(0, snapshot.position))
                .putInt(DURATION, Math.max(0, snapshot.duration))
                .putInt(INDEX, Math.max(0, snapshot.index))
                .putInt(LOOP_MODE, snapshot.loopMode)
                .putBoolean(PLAYING, snapshot.playing)
                .putBoolean(SHUFFLE, snapshot.shuffle)
                .putLong(SAVED_AT, now)
                .putLong(INACTIVE_SINCE, inactiveSince);
        if (includeQueue || !queueJson.equals(lastSavedQueueJson)) {
            editor.putString(QUEUE, queueJson);
            lastSavedQueueJson = queueJson;
        }
        if (includeQueue) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    public void save(PlaybackSnapshot snapshot, String currentUri, List<Track> queue,
            boolean includeQueue) {
        boolean playbackActive = MiniPlayerRetentionPolicy.isPlaybackActive(
                snapshot.playWhenReady, snapshot.phase, snapshot.stopReason);
        save(new Snapshot(currentUri, (int) Math.min(Integer.MAX_VALUE, snapshot.positionMs),
                (int) Math.min(Integer.MAX_VALUE, snapshot.durationMs), snapshot.currentIndex,
                RepeatModeMapper.fromMedia3(snapshot.repeatMode),
                playbackActive, snapshot.shuffleEnabled, queue), includeQueue);
    }

    public void save(PlaybackSnapshot snapshot, String currentUri, boolean includeQueue) {
        boolean playbackActive = MiniPlayerRetentionPolicy.isPlaybackActive(
                snapshot.playWhenReady, snapshot.phase, snapshot.stopReason);
        save(new Snapshot(currentUri, (int) Math.min(Integer.MAX_VALUE, snapshot.positionMs),
                (int) Math.min(Integer.MAX_VALUE, snapshot.durationMs), snapshot.currentIndex,
                RepeatModeMapper.fromMedia3(snapshot.repeatMode), playbackActive,
                snapshot.shuffleEnabled, new ArrayList<>(snapshot.queueMediaIds)), includeQueue);
    }

    public void clear() {
        lastSavedQueueJson = "";
        preferences.edit().clear().apply();
    }

    public void removeTracks(Set<String> trackIds, Set<String> trackUris) {
        State state = load();
        QueueRemovalPlan plan = QueueRemovalPlan.create(
                state.queueUris, state.index, trackIds);
        if (plan.remaining.isEmpty()) {
            clear();
            return;
        }
        boolean currentRemoved = trackUris.contains(state.uri)
                || plan.currentRemoved;
        save(new Snapshot(currentRemoved ? "" : state.uri,
                currentRemoved ? 0 : state.position, state.duration, plan.currentIndex,
                state.loopMode, state.playing, state.shuffle, plan.remaining), true);
    }

    public static final class State {
        public final String uri;
        public final int position;
        public final int duration;
        public final int index;
        public final int loopMode;
        public final boolean playing;
        public final boolean shuffle;
        public final long savedAt;
        public final long inactiveSince;
        public final ArrayList<String> queueUris;

        State(String uri, int position, int duration, int index, int loopMode, boolean playing,
                boolean shuffle, long savedAt, long inactiveSince,
                ArrayList<String> queueUris) {
            this.uri = uri;
            this.position = position;
            this.duration = duration;
            this.index = index;
            this.loopMode = loopMode;
            this.playing = playing;
            this.shuffle = shuffle;
            this.savedAt = savedAt;
            this.inactiveSince = inactiveSince;
            this.queueUris = queueUris;
        }
    }

    public static final class Snapshot {
        final String uri;
        final int position;
        final int duration;
        final int index;
        final int loopMode;
        final boolean playing;
        final boolean shuffle;
        final ArrayList<String> queueUris;

        public Snapshot(String uri, int position, int duration, int index, int loopMode,
                boolean playing, boolean shuffle, List<Track> queue) {
            this(uri, position, duration, index, loopMode, playing, shuffle, trackIds(queue));
        }

        public Snapshot(String uri, int position, int duration, int index, int loopMode,
                boolean playing, boolean shuffle, ArrayList<String> queueIds) {
            this.uri = uri == null ? "" : uri;
            this.position = position;
            this.duration = duration;
            this.index = index;
            this.loopMode = loopMode;
            this.playing = playing;
            this.shuffle = shuffle;
            this.queueUris = queueIds;
        }

        private static ArrayList<String> trackIds(List<Track> queue) {
            ArrayList<String> ids = new ArrayList<>();
            for (Track track : queue) {
                ids.add(track.trackId);
            }
            return ids;
        }
    }

    private static ArrayList<String> queueUris(String json) {
        ArrayList<String> uris = new ArrayList<>();
        try {
            JSONArray queue = new JSONArray(json == null ? "[]" : json);
            for (int index = 0; index < queue.length(); index++) {
                String uri = queue.optString(index, "");
                if (!uri.isEmpty()) {
                    uris.add(uri);
                }
            }
        } catch (Exception ignored) {
            // A damaged saved queue is treated as empty and falls back to the current track.
        }
        return uris;
    }

    private static String queueJson(List<String> uris) {
        JSONArray queue = new JSONArray();
        for (String uri : uris) {
            queue.put(uri);
        }
        return queue.toString();
    }
}
