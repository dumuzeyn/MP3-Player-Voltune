package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

final class SoundAnalysisConstraints {
    enum BlockReason {
        NONE,
        PLAYBACK,
        LOW_BATTERY,
        THERMAL
    }

    private SoundAnalysisConstraints() {
    }

    static BlockReason reason(Context context, boolean playbackActive) {
        if (playbackActive) {
            return BlockReason.PLAYBACK;
        }
        if (isThermallyConstrained(context)) {
            return BlockReason.THERMAL;
        }
        if (isBatteryLow(context)) {
            return BlockReason.LOW_BATTERY;
        }
        return BlockReason.NONE;
    }

    private static boolean isThermallyConstrained(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return manager != null
                && manager.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE;
    }

    private static boolean isBatteryLow(Context context) {
        Intent battery = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return false;
        }
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percentage = level < 0 || scale <= 0 ? 100 : level * 100 / scale;
        return !charging && percentage <= 15;
    }
}
