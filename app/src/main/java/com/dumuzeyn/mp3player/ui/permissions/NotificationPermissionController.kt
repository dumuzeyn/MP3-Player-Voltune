package com.dumuzeyn.mp3player.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

object NotificationPermissionController {
    private const val REQUEST_NOTIFICATIONS = 33

    @JvmStatic
    fun requestIfNeeded(activity: Activity) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS,
            )
        }
    }
}
