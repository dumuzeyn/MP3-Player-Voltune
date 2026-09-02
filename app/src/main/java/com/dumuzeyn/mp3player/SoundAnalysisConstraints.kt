package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

object SoundAnalysisConstraints {
    enum class BlockReason { NONE, PLAYBACK, LOW_BATTERY, THERMAL }

    @JvmStatic
    fun reason(context: Context, playbackActive: Boolean): BlockReason = when {
        playbackActive -> BlockReason.PLAYBACK
        isThermallyConstrained(context) -> BlockReason.THERMAL
        isBatteryLow(context) -> BlockReason.LOW_BATTERY
        else -> BlockReason.NONE
    }

    private fun isThermallyConstrained(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return manager?.currentThermalStatus?.let { it >= PowerManager.THERMAL_STATUS_SEVERE } == true
    }

    private fun isBatteryLow(context: Context): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percentage = if (level < 0 || scale <= 0) 100 else level * 100 / scale
        return !charging && percentage <= 15
    }
}
