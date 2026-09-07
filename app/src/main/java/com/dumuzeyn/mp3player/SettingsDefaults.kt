package com.dumuzeyn.mp3player

import android.content.Context
import android.content.pm.PackageManager
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager

/** Applies the requested 2.4.3 settings reset once without touching the SQLite library. */
object SettingsDefaults {
    private const val MIGRATION_PREFS = "voltune_migrations"
    private const val RESET_2_4_3 = "settings_reset_2_4_3"

    @JvmStatic
    fun resetForVersion243(context: Context) {
        val migrations = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (migrations.getBoolean(RESET_2_4_3, false)) return

        // Move legacy favorites and playlists to SQLite before clearing old UI preferences.
        LibraryDatabase.migrateLegacyIfNeeded(context)
        clear(context, "mp3_player_ui")
        clear(context, UninterruptedPlaybackController.PREFS)
        clear(context, EqualizerController.PREFS)
        clear(context, PlaybackStateManager.PREFS)
        clear(context, "player_sleep_timer")
        resetLauncherAlias(context)
        migrations.edit().putBoolean(RESET_2_4_3, true).commit()
    }

    private fun clear(context: Context, name: String) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun resetLauncherAlias(context: Context) {
        val manager = context.packageManager
        val light = LauncherComponents.forTheme(context, false)
        try {
            manager.setComponentEnabledSetting(
                light,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            LauncherComponents.all(context).forEach { component ->
                if (component != light) {
                    manager.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        } catch (_: RuntimeException) {
            // Some launchers postpone alias updates until the activity is no longer visible.
        }
    }
}
