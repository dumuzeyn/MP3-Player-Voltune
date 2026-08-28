package com.dumuzeyn.mp3player;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.util.List;

/** Keeps the launcher and next splash aligned when Android changes system night mode. */
public final class ThemeConfigurationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = UiPreferencesStore.readThemeMode(context);
        if (!"system".equals(mode) || isAppForeground(context)) {
            return;
        }
        boolean dark = ThemeController.isSystemDark(context);
        ComponentName selected = LauncherComponents.forThemeState(context, mode, dark,
                UiPreferencesStore.readCustomBackground(context),
                UiPreferencesStore.readCustomForeground(context),
                UiPreferencesStore.readCustomSecondaryAccent(context));
        try {
            LauncherComponents.apply(context, selected);
        } catch (RuntimeException ignored) {
            // The activity retries the same state during its next lifecycle transition.
        }
    }

    private static boolean isAppForeground(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) {
            return false;
        }
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.uid == android.os.Process.myUid()
                    && process.importance
                    <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }
}
