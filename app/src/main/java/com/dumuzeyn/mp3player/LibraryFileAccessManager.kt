package com.dumuzeyn.mp3player

import android.content.Context
import com.dumuzeyn.mp3player.ui.permissions.DeviceAudioPermissionController

object LibraryFileAccessManager {
    enum class AccessState { AVAILABLE, UNAVAILABLE, DEFERRED }

    @JvmStatic
    fun accessState(context: Context, track: Track?): AccessState {
        if (track == null || track.uri.isBlank()) return AccessState.UNAVAILABLE
        val uri = runCatching(track::asUri).getOrElse { return AccessState.UNAVAILABLE }
        if (
            uri.authority.equals("media", ignoreCase = true) &&
            !DeviceAudioPermissionController.hasPermission(context)
        ) {
            return AccessState.DEFERRED
        }
        return if (TrackStore.canOpenForRead(context, uri)) {
            AccessState.AVAILABLE
        } else {
            AccessState.UNAVAILABLE
        }
    }
}
