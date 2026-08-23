package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PersistedFolderStore {
    private static final String PREFS = "voltune_music_folders";
    private static final String TREES = "trees";

    private PersistedFolderStore() {
    }

    static void remember(Context context, Uri treeUri) {
        if (!hasReadPermission(context, treeUri)) {
            return;
        }
        Set<String> trees = new HashSet<>(context.getSharedPreferences(PREFS,
                Context.MODE_PRIVATE).getStringSet(TREES, new HashSet<String>()));
        trees.add(treeUri.toString());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(TREES, trees).apply();
    }

    static List<Uri> readableTrees(Context context) {
        ArrayList<Uri> result = new ArrayList<>();
        Set<String> stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(TREES, new HashSet<String>());
        for (String value : stored) {
            Uri uri = Uri.parse(value);
            if (hasReadPermission(context, uri)) {
                result.add(uri);
            }
        }
        return result;
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
}
