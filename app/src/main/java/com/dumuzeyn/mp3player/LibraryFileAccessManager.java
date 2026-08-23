package com.dumuzeyn.mp3player;

import android.content.Context;
import com.dumuzeyn.mp3player.ui.permissions.DeviceAudioPermissionController;

final class LibraryFileAccessManager {
    enum AccessState {
        AVAILABLE,
        UNAVAILABLE,
        DEFERRED
    }

    private LibraryFileAccessManager() {
    }

    static AccessState accessState(Context context, Track track) {
        if (track == null || track.uri == null || track.uri.trim().isEmpty()) {
            return AccessState.UNAVAILABLE;
        }
        android.net.Uri uri;
        try {
            uri = track.asUri();
        } catch (RuntimeException invalidUri) {
            return AccessState.UNAVAILABLE;
        }
        String authority = uri.getAuthority();
        if ("media".equalsIgnoreCase(authority)
                && !DeviceAudioPermissionController.hasPermission(context)) {
            return AccessState.DEFERRED;
        }
        return TrackStore.canOpenForRead(context, uri)
                ? AccessState.AVAILABLE : AccessState.UNAVAILABLE;
    }

}
