package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Versioned, path-free serialization used by persistence tests and diagnostics. */
public final class PlaybackSnapshotCodec {
    private static final int VERSION = 1;

    private PlaybackSnapshotCodec() {
    }

    public static String encode(PlaybackSnapshot snapshot) {
        try {
            JSONObject json = new JSONObject();
            json.put("version", VERSION);
            json.put("queueMediaIds", new JSONArray(snapshot.queueMediaIds));
            json.put("currentMediaId", snapshot.currentMediaId);
            json.put("currentIndex", snapshot.currentIndex);
            json.put("positionMs", snapshot.positionMs);
            json.put("durationMs", snapshot.durationMs);
            json.put("playWhenReady", snapshot.playWhenReady);
            json.put("playbackState", snapshot.playbackState);
            json.put("repeatMode", snapshot.repeatMode);
            json.put("shuffleEnabled", snapshot.shuffleEnabled);
            json.put("phase", snapshot.phase.name());
            json.put("pauseReason", snapshot.pauseReason.name());
            json.put("stopReason", snapshot.stopReason.name());
            json.put("updatedAt", snapshot.updatedAt);
            return json.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Playback snapshot could not be encoded", error);
        }
    }

    public static PlaybackSnapshot decode(String encoded) {
        try {
            JSONObject json = new JSONObject(encoded == null ? "{}" : encoded);
            if (json.optInt("version", -1) != VERSION) {
                return PlaybackSnapshot.empty();
            }
            ArrayList<String> queue = new ArrayList<>();
            JSONArray items = json.optJSONArray("queueMediaIds");
            if (items != null) {
                for (int index = 0; index < items.length(); index++) {
                    String mediaId = items.optString(index, "");
                    if (!mediaId.isEmpty()) {
                        queue.add(mediaId);
                    }
                }
            }
            return new PlaybackSnapshot(queue, json.optString("currentMediaId", ""),
                    json.optInt("currentIndex", -1), json.optLong("positionMs", 0L),
                    json.optLong("durationMs", 0L), json.optBoolean("playWhenReady", false),
                    json.optInt("playbackState", 1), json.optInt("repeatMode", 0),
                    json.optBoolean("shuffleEnabled", false),
                    enumValue(PlaybackPhase.class, json.optString("phase"), PlaybackPhase.IDLE),
                    enumValue(PauseReason.class, json.optString("pauseReason"), PauseReason.NONE),
                    enumValue(StopReason.class, json.optString("stopReason"), StopReason.NONE),
                    null, json.optLong("updatedAt", System.currentTimeMillis()));
        } catch (Exception ignored) {
            return PlaybackSnapshot.empty();
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
