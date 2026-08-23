package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.SharedPreferences;

/** Cheap cross-component signal that listening statistics changed in SQLite. */
final class LibraryContentVersion {
    private static final String PREFS = "library_content_version";
    private static final String VALUE = "value";

    private LibraryContentVersion() {
    }

    static long read(Context context) {
        return preferences(context).getLong(VALUE, 0L);
    }

    static synchronized void bump(Context context) {
        SharedPreferences preferences = preferences(context);
        long previous = preferences.getLong(VALUE, 0L);
        long next = Math.max(System.currentTimeMillis(), previous + 1L);
        preferences.edit().putLong(VALUE, next).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
