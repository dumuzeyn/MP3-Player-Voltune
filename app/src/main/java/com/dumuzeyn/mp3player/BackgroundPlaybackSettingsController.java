package com.dumuzeyn.mp3player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

/** Explains OS background restrictions and opens the app-specific system page. */
final class BackgroundPlaybackSettingsController {
    private static final String PREFS = "background_playback_setup";
    private static final String PROMPTED = "prompted_v1";
    private static final int REQUEST_APP_SETTINGS = 7310;
    private static final int REQUEST_BATTERY_EXEMPTION = 7311;

    private final MainActivityCore host;

    BackgroundPlaybackSettingsController(MainActivityCore host) {
        this.host = host;
    }

    String settingLabel() {
        boolean setupNeeded = needsSetup();
        return host.tr("Background mode: ", "Фоновый режим: ")
                + host.tr(setupNeeded ? "configure" : "protected",
                        setupNeeded ? "настроить" : "защищён");
    }

    void maybePromptOnce() {
        SharedPreferences preferences = host.getSharedPreferences(PREFS, 0);
        if (!needsSetup() || preferences.getBoolean(PROMPTED, false)) {
            return;
        }
        preferences.edit().putBoolean(PROMPTED, true).apply();
        host.showActionPanel(
                host.tr("Protect background playback", "Защитить фоновое воспроизведение"),
                explanation(),
                host.tr("Later", "Позже"),
                host.tr("Configure", "Настроить"),
                true,
                this::beginGuidedSetup);
    }

    void openDialog() {
        boolean setupNeeded = needsSetup();
        String details = setupNeeded
                ? explanation()
                : host.tr(
                        "Required restrictions are already disabled. Voltune can keep playing in the background.",
                        "Нужные ограничения уже отключены. Voltune может продолжать играть в фоне.");
        host.showActionPanel(
                host.tr("Background playback", "Фоновое воспроизведение"),
                statusText() + "\n\n" + details,
                host.tr("Cancel", "Отмена"),
                host.tr(setupNeeded ? "Configure" : "Review",
                        setupNeeded ? "Настроить" : "Проверить"),
                true,
                this::beginGuidedSetup);
    }

    boolean handleActivityResult(int requestCode) {
        if (requestCode == REQUEST_BATTERY_EXEMPTION) {
            openAppSettings();
            return true;
        }
        if (requestCode == REQUEST_APP_SETTINGS) {
            if (host.navigationState.tabIndex == 6) {
                host.refreshSettingsLabels();
            }
            return true;
        }
        return false;
    }

    private boolean needsSetup() {
        return !ignoresBatteryOptimizations() || !unusedAppPauseDisabled();
    }

    private boolean ignoresBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = (PowerManager) host.getSystemService(MainActivityCore.POWER_SERVICE);
        return powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(host.getPackageName());
    }

    private boolean unusedAppPauseDisabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        try {
            return host.getPackageManager().isAutoRevokeWhitelisted();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String statusText() {
        boolean batteryUnrestricted = ignoresBatteryOptimizations();
        String battery = host.tr("Battery restrictions: ", "Ограничения батареи: ")
                + host.tr(batteryUnrestricted ? "unrestricted" : "active",
                        batteryUnrestricted ? "сняты" : "активны");
        String unused;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            unused = host.tr("Pause when unused: check in system settings",
                    "Приостановка при неиспользовании: проверьте в настройках системы");
        } else {
            boolean pauseDisabled = unusedAppPauseDisabled();
            unused = host.tr("Pause when unused: ", "Приостановка при неиспользовании: ")
                    + host.tr(pauseDisabled ? "off" : "on",
                            pauseDisabled ? "выключена" : "включена");
        }
        return battery + "\n" + unused;
    }

    private String explanation() {
        return host.tr(
                "Voltune will request unrestricted battery use, then open its system page. "
                        + "Scroll to the bottom and turn off 'Pause app activity if unused'. "
                        + "Android requires your confirmation for both settings.",
                "Voltune запросит работу без ограничений батареи, затем откроет свою системную страницу. "
                        + "Прокрутите её вниз и выключите «Приостанавливать работу в неактивный период». "
                        + "Android требует вашего подтверждения для этих настроек.");
    }

    private void beginGuidedSetup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ignoresBatteryOptimizations()) {
            openAppSettings();
            return;
        }
        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + host.getPackageName()));
        try {
            host.startActivityForResult(request, REQUEST_BATTERY_EXEMPTION);
        } catch (RuntimeException error) {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        Toast.makeText(
                host,
                host.tr("Scroll to the bottom and turn off pause when unused",
                        "Прокрутите вниз и выключите приостановку в неактивный период"),
                Toast.LENGTH_LONG).show();
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent = new Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                    Uri.parse("package:" + host.getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + host.getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            host.startActivityForResult(intent, REQUEST_APP_SETTINGS);
        } catch (RuntimeException error) {
            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
            host.startActivity(fallback);
        }
    }
}
