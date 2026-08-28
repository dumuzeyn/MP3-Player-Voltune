package com.dumuzeyn.mp3player;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Keeps the launcher and next splash aligned when Android changes system night mode. */
public final class ThemeConfigurationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = UiPreferencesStore.readThemeMode(context);
        if (!"system".equals(mode)) {
            return;
        }
        boolean dark = ThemeController.isSystemDark(context);
        ComponentName selected = LauncherComponents.forThemeState(context, mode, dark,
                UiPreferencesStore.readCustomBackground(context));
        try {
            LauncherComponents.apply(context, selected);
        } catch (RuntimeException ignored) {
            // The activity retries the same state during its next lifecycle transition.
        }
    }
}
