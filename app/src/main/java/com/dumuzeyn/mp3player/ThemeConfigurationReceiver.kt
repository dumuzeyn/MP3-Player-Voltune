package com.dumuzeyn.mp3player

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process

/** Keeps the launcher and next splash aligned when Android changes system night mode. */
class ThemeConfigurationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = UiPreferencesStore.readThemeMode(context)
        if (mode != "system" || isAppForeground(context)) return

        val selected = LauncherComponents.forThemeState(
            context,
            mode,
            ThemeController.isSystemDark(context),
            UiPreferencesStore.readCustomBackground(context),
            UiPreferencesStore.readCustomForeground(context),
            UiPreferencesStore.readCustomSecondaryAccent(context),
        )
        try {
            LauncherComponents.apply(context, selected)
        } catch (_: RuntimeException) {
            // The activity retries the same state during its next lifecycle transition.
        }
    }

    private fun isAppForeground(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val processes = manager.runningAppProcesses ?: return false
        return processes.any { process ->
            process.uid == Process.myUid() &&
                process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
