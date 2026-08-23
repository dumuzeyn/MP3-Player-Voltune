package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class LibraryBackupManager {
    static final int MAX_BACKUP_BYTES = 1024 * 1024;
    private static final int SCHEMA_VERSION = 1;
    private static final String UI_PREFS = "mp3_player_ui";

    private LibraryBackupManager() {
    }

    static String exportBackup(Context context, List<Track> tracks,
            List<Playlist> playlists) throws Exception {
        Map<String, String> idsByUri = new HashMap<>();
        for (Track track : tracks) {
            idsByUri.put(track.uri, track.trackId);
        }
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        JSONArray playlistArray = new JSONArray();
        for (Playlist playlist : playlists) {
            JSONObject item = new JSONObject();
            item.put("name", PlaylistManager.cleanName(playlist.name));
            JSONArray ids = new JSONArray();
            for (String uri : playlist.uris) {
                String trackId = idsByUri.get(uri);
                if (trackId != null) {
                    ids.put(trackId);
                }
            }
            item.put("trackIds", ids);
            playlistArray.put(item);
        }
        root.put("playlists", playlistArray);
        root.put("settings", encodeSettings(context.getSharedPreferences(UI_PREFS,
                Context.MODE_PRIVATE)));
        String encoded = root.toString(2);
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_BACKUP_BYTES) {
            throw new IllegalArgumentException("Backup is too large");
        }
        return encoded;
    }

    static ImportResult importBackup(Context context, String encoded, List<Track> tracks)
            throws Exception {
        if (encoded == null || encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_BACKUP_BYTES) {
            throw new IllegalArgumentException("Backup is empty or too large");
        }
        JSONObject root = new JSONObject(encoded);
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported backup schema");
        }
        Map<String, String> urisById = new HashMap<>();
        for (Track track : tracks) {
            urisById.put(track.trackId, track.uri);
        }
        ArrayList<Playlist> playlists = new ArrayList<>();
        JSONArray playlistArray = root.optJSONArray("playlists");
        if (playlistArray != null) {
            for (int index = 0; index < Math.min(playlistArray.length(), 1000); index++) {
                JSONObject item = playlistArray.getJSONObject(index);
                Playlist playlist = new Playlist(
                        PlaylistManager.cleanName(item.optString("name", "Playlist")));
                JSONArray ids = item.optJSONArray("trackIds");
                if (ids != null) {
                    for (int songIndex = 0; songIndex < Math.min(ids.length(), 10000);
                            songIndex++) {
                        String uri = urisById.get(ids.optString(songIndex, ""));
                        if (uri != null && !playlist.uris.contains(uri)) {
                            playlist.uris.add(uri);
                        }
                    }
                }
                playlists.add(playlist);
            }
        }
        restoreSettings(context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE),
                root.optJSONObject("settings"));
        return new ImportResult(playlists);
    }

    private static JSONObject encodeSettings(SharedPreferences preferences) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof String) {
                result.put(entry.getKey(), value);
            } else if (value instanceof Set) {
                JSONArray values = new JSONArray();
                for (Object item : (Set<?>) value) {
                    if (item instanceof String) {
                        values.put(item);
                    }
                }
                result.put(entry.getKey(), values);
            }
        }
        return result;
    }

    private static void restoreSettings(SharedPreferences preferences, JSONObject settings)
            throws Exception {
        if (settings == null || settings.length() > 300) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        JSONArray names = settings.names();
        if (names == null) {
            return;
        }
        for (int index = 0; index < names.length(); index++) {
            String key = names.getString(index);
            Object value = settings.get(key);
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Double) {
                double number = (Double) value;
                if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
                        && number == Math.rint(number)) {
                    editor.putInt(key, (int) number);
                } else {
                    editor.putFloat(key, (float) number);
                }
            } else if (value instanceof String && ((String) value).length() <= 4096) {
                editor.putString(key, (String) value);
            } else if (value instanceof JSONArray) {
                HashSet<String> values = new HashSet<>();
                JSONArray array = (JSONArray) value;
                for (int itemIndex = 0; itemIndex < Math.min(array.length(), 1000);
                        itemIndex++) {
                    String item = array.optString(itemIndex, "");
                    if (item.length() <= 4096) {
                        values.add(item);
                    }
                }
                editor.putStringSet(key, values);
            }
        }
        editor.commit();
    }

    static final class ImportResult {
        final ArrayList<Playlist> playlists;

        ImportResult(ArrayList<Playlist> playlists) {
            this.playlists = playlists;
        }
    }
}
