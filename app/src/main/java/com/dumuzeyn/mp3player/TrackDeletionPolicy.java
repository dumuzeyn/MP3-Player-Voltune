package com.dumuzeyn.mp3player;

import android.net.Uri;

final class TrackDeletionPolicy {
    private TrackDeletionPolicy() {
    }

    static boolean isMediaStore(Uri uri) {
        return uri != null && isMediaStore(uri.toString());
    }

    static boolean isContentUri(Uri uri) {
        return uri != null && isContentUri(uri.toString());
    }

    static boolean isMediaStore(String uri) {
        return uri != null && uri.startsWith("content://media/");
    }

    static boolean isContentUri(String uri) {
        return uri != null && uri.startsWith("content://");
    }
}
