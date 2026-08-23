package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Compatibility boundary around SQLite-backed SAF music sources. */
final class PersistedFolderStore {
    private static final String PREFS = "voltune_music_folders";
    private static final String TREES = "trees";
    private static final String MIGRATED = "sources_migrated_to_sqlite";

    private PersistedFolderStore() {
    }

    static LibrarySource remember(Context context, Uri treeUri) {
        return remember(context, treeUri, fallbackName(treeUri), true);
    }

    static LibrarySource remember(Context context, Uri treeUri, String displayName,
            boolean explicitImport) {
        if (!hasReadPermission(context, treeUri)) {
            return null;
        }
        migrateLegacy(context);
        LibrarySourceStore store = new LibrarySourceStore(context);
        try {
            return store.remember(treeUri, displayName, explicitImport);
        } finally {
            store.close();
        }
    }

    static List<LibrarySource> list(Context context) {
        migrateLegacy(context);
        LibrarySourceStore store = new LibrarySourceStore(context);
        try {
            return store.list();
        } finally {
            store.close();
        }
    }

    static List<Uri> readableTrees(Context context) {
        ArrayList<Uri> result = new ArrayList<>();
        for (LibrarySource source : list(context)) {
            Uri uri = source.asUri();
            if (hasReadPermission(context, uri)) {
                result.add(uri);
            }
        }
        return result;
    }

    static boolean contains(Context context, Uri treeUri) {
        migrateLegacy(context);
        LibrarySourceStore store = new LibrarySourceStore(context);
        try {
            return store.find(treeUri) != null;
        } finally {
            store.close();
        }
    }

    static RemovedLibraryItems forget(Context context, LibrarySource source) {
        LibraryMutationStore store = new LibraryMutationStore(context);
        try {
            return store.removeSource(source.sourceId);
        } finally {
            store.close();
        }
    }

    static RemovedLibraryItems clear(Context context) {
        LibraryMutationStore store = new LibraryMutationStore(context);
        try {
            return store.clearLibrary();
        } finally {
            store.close();
        }
    }

    static void releaseReadPermission(Context context, Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            context.getContentResolver().releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Permission may already have been revoked by Android or the provider.
        }
    }

    static boolean hasReadPermission(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        for (UriPermission permission : context.getContentResolver()
                .getPersistedUriPermissions()) {
            if (permission.isReadPermission() && uri.equals(permission.getUri())) {
                return true;
            }
        }
        return false;
    }

    static int persistReadFlag(int resultFlags) {
        int flags = resultFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        return flags == 0 ? Intent.FLAG_GRANT_READ_URI_PERMISSION : flags;
    }

    private static void migrateLegacy(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(PREFS,
                Context.MODE_PRIVATE);
        if (preferences.getBoolean(MIGRATED, false)) {
            return;
        }
        Set<String> legacy = new HashSet<>(preferences.getStringSet(TREES,
                new HashSet<String>()));
        LibrarySourceStore store = new LibrarySourceStore(context);
        try {
            for (String value : legacy) {
                Uri uri = Uri.parse(value);
                if (hasReadPermission(context, uri)) {
                    store.remember(uri, fallbackName(uri), false);
                }
            }
            preferences.edit().remove(TREES).putBoolean(MIGRATED, true).commit();
        } finally {
            store.close();
        }
    }

    private static String fallbackName(Uri uri) {
        String segment = uri == null ? "" : uri.getLastPathSegment();
        return segment == null || segment.trim().isEmpty() ? "Music folder" : segment;
    }
}
