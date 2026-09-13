package com.dumuzeyn.mp3player.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Owns the runtime permission required for automatic MediaStore music discovery. */
object DeviceAudioPermissionController {
    const val REQUEST_AUDIO_LIBRARY = 34

    @JvmStatic
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.checkSelfPermission(requiredPermission()) == PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun requestIfNeeded(activity: Activity): Boolean {
        if (hasPermission(activity)) return false
        activity.requestPermissions(arrayOf(requiredPermission()), REQUEST_AUDIO_LIBRARY)
        return true
    }

    @JvmStatic
    fun handles(requestCode: Int): Boolean = requestCode == REQUEST_AUDIO_LIBRARY

    private fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
