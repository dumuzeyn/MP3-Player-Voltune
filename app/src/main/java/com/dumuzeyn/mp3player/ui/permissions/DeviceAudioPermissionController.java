package com.dumuzeyn.mp3player.ui.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Owns the runtime permission required for automatic MediaStore music discovery. */
public final class DeviceAudioPermissionController {
    public static final int REQUEST_AUDIO_LIBRARY = 34;

    private DeviceAudioPermissionController() {
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return context.checkSelfPermission(requiredPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean requestIfNeeded(Activity activity) {
        if (hasPermission(activity)) {
            return false;
        }
        activity.requestPermissions(
                new String[]{requiredPermission()}, REQUEST_AUDIO_LIBRARY);
        return true;
    }

    public static boolean handles(int requestCode) {
        return requestCode == REQUEST_AUDIO_LIBRARY;
    }

    private static String requiredPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }
}
