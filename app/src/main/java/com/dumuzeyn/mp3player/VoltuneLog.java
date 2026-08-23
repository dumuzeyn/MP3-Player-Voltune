package com.dumuzeyn.mp3player;

import android.util.Log;

/** Debug-only logging that never records URIs, paths, titles, or exception messages. */
public final class VoltuneLog {
    private static final String TAG = "VoltuneDebug";

    private VoltuneLog() {
    }

    public static void info(String event) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, event);
        }
    }

    public static void warning(String event) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, event);
        }
    }

    public static void failure(String event, Throwable error) {
        if (BuildConfig.DEBUG) {
            String category = error == null ? "unknown" : error.getClass().getSimpleName();
            Log.e(TAG, event + " category=" + category);
        }
    }
}
