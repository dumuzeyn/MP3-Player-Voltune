package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Stores a bounded, path-free event history for user-initiated diagnostics exports. */
final class PlaybackEventLogger {
    private static final String PREFS = "playback_diagnostics_v1";
    private static final String EVENTS = "events";
    private static final int MAX_EVENTS = 200;

    private final SharedPreferences preferences;
    private JSONArray events;

    PlaybackEventLogger(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        events = parseEvents(preferences.getString(EVENTS, "[]"));
    }

    synchronized void record(String type, PlaybackSnapshot snapshot, String errorCategory,
            String audioFocus, boolean mediaSessionActive, boolean foregroundActive) {
        if (snapshot == null) {
            return;
        }
        JSONObject event = new JSONObject();
        try {
            event.put("timestamp", System.currentTimeMillis());
            event.put("type", safeToken(type, "unknown"));
            event.put("phase", snapshot.phase.name());
            event.put("pauseReason", snapshot.pauseReason.name());
            event.put("stopReason", snapshot.stopReason.name());
            event.put("currentIndex", snapshot.currentIndex);
            event.put("queueSize", snapshot.queueMediaIds.size());
            event.put("mediaId", opaqueMediaId(snapshot.currentMediaId));
            event.put("errorCategory", safeToken(errorCategory, "none"));
            event.put("audioFocus", safeToken(audioFocus, "unknown"));
            event.put("mediaSessionActive", mediaSessionActive);
            event.put("foregroundActive", foregroundActive);
            events.put(event);
            trim();
            preferences.edit().putString(EVENTS, events.toString()).apply();
        } catch (Exception ignored) {
            // Diagnostics must never interfere with playback.
        }
    }

    static String buildReport(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray events = parseEvents(preferences.getString(EVENTS, "[]"));
        JSONObject latest = events.length() == 0 ? new JSONObject()
                : events.optJSONObject(events.length() - 1);
        StringBuilder report = new StringBuilder();
        report.append("Voltune playback diagnostics\n");
        report.append("createdAt=").append(utcTimestamp()).append('\n');
        report.append("version=").append(appVersion(context)).append('\n');
        report.append("android=").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("device=").append(safeDevice(Build.MANUFACTURER))
                .append(' ').append(safeDevice(Build.MODEL)).append('\n');
        report.append("eventCount=").append(events.length()).append('\n');
        if (latest != null) {
            appendLatest(report, latest);
        }
        report.append("\nEvents (oldest to newest; paths, URIs, file names and tags excluded):\n");
        for (int index = 0; index < events.length(); index++) {
            JSONObject event = events.optJSONObject(index);
            if (event != null) {
                report.append(event.toString()).append('\n');
            }
        }
        return report.toString();
    }

    static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    static String opaqueMediaId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "none";
        }
        if (value.contains("://") || value.contains("/") || value.contains("\\")) {
            return TrackIdentity.fromLegacyUri(value);
        }
        return safeToken(value, "unknown");
    }

    private synchronized void trim() {
        if (events.length() <= MAX_EVENTS) {
            return;
        }
        JSONArray trimmed = new JSONArray();
        int start = events.length() - MAX_EVENTS;
        for (int index = start; index < events.length(); index++) {
            trimmed.put(events.opt(index));
        }
        events = trimmed;
    }

    private static JSONArray parseEvents(String encoded) {
        try {
            return new JSONArray(encoded == null ? "[]" : encoded);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void appendLatest(StringBuilder report, JSONObject latest) {
        report.append("phase=").append(latest.optString("phase", "unknown")).append('\n');
        report.append("pauseReason=").append(latest.optString("pauseReason", "unknown"))
                .append('\n');
        report.append("stopReason=").append(latest.optString("stopReason", "unknown"))
                .append('\n');
        report.append("queueSize=").append(latest.optInt("queueSize", 0)).append('\n');
        report.append("currentIndex=").append(latest.optInt("currentIndex", -1)).append('\n');
        report.append("mediaId=").append(latest.optString("mediaId", "none")).append('\n');
        report.append("lastEvent=").append(latest.optString("type", "unknown")).append('\n');
        report.append("lastError=").append(latest.optString("errorCategory", "none"))
                .append('\n');
        report.append("audioFocus=").append(latest.optString("audioFocus", "unknown"))
                .append('\n');
        report.append("mediaSessionActive=")
                .append(latest.optBoolean("mediaSessionActive", false)).append('\n');
        report.append("foregroundActive=")
                .append(latest.optBoolean("foregroundActive", false)).append('\n');
    }

    private static String safeToken(String value, String fallback) {
        String source = value == null || value.trim().isEmpty() ? fallback : value;
        String safe = source.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.length() <= 96 ? safe : safe.substring(0, 96);
    }

    private static String safeDevice(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\r\\n]", " ");
    }

    @SuppressWarnings("deprecation")
    private static String appVersion(Context context) {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                        android.content.pm.PackageManager.PackageInfoFlags.of(0));
            } else {
                info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            }
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String utcTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
