package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/** Stores a bounded, path-free event history for user-initiated diagnostics exports. */
class PlaybackEventLogger(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var events = parseEvents(preferences.getString(EVENTS, "[]"))

    @Synchronized
    fun record(
        type: String?,
        snapshot: PlaybackSnapshot?,
        errorCategory: String?,
        audioFocus: String?,
        mediaSessionActive: Boolean,
        foregroundActive: Boolean,
    ) {
        snapshot ?: return
        runCatching {
            events.put(
                JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("type", safeToken(type, "unknown"))
                    put("phase", snapshot.phase.name)
                    put("pauseReason", snapshot.pauseReason.name)
                    put("stopReason", snapshot.stopReason.name)
                    put("currentIndex", snapshot.currentIndex)
                    put("queueSize", snapshot.queueMediaIds.size)
                    put("mediaId", opaqueMediaId(snapshot.currentMediaId))
                    put("errorCategory", safeToken(errorCategory, "none"))
                    put("audioFocus", safeToken(audioFocus, "unknown"))
                    put("mediaSessionActive", mediaSessionActive)
                    put("foregroundActive", foregroundActive)
                },
            )
            trim()
            preferences.edit().putString(EVENTS, events.toString()).apply()
        }
    }

    @Synchronized
    private fun trim() {
        if (events.length() <= MAX_EVENTS) return
        events = JSONArray().apply {
            for (index in events.length() - MAX_EVENTS..<events.length()) put(events.opt(index))
        }
    }

    companion object {
        private const val PREFS = "playback_diagnostics_v1"
        private const val EVENTS = "events"
        private const val MAX_EVENTS = 200
        private val unsafeToken = Regex("[^A-Za-z0-9_.:-]")
        private val unsafeDevice = Regex("[\\r\\n]")

        @JvmStatic
        fun buildReport(context: Context): String {
            val events = parseEvents(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(EVENTS, "[]"),
            )
            val latest = if (events.length() == 0) JSONObject() else events.optJSONObject(events.length() - 1)
            return buildString {
                appendLine("Voltune playback diagnostics")
                appendLine("createdAt=${utcTimestamp()}")
                appendLine("version=${appVersion(context)}")
                appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("device=${safeDevice(Build.MANUFACTURER)} ${safeDevice(Build.MODEL)}")
                appendLine("eventCount=${events.length()}")
                if (latest != null) appendLatest(latest)
                appendLine()
                appendLine("Events (oldest to newest; paths, URIs, file names and tags excluded):")
                repeat(events.length()) { index ->
                    events.optJSONObject(index)?.let { appendLine(it.toString()) }
                }
            }
        }

        @JvmStatic
        fun clear(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }

        @JvmStatic
        fun opaqueMediaId(value: String?): String {
            if (value.isNullOrBlank()) return "none"
            return if (value.contains("://") || value.contains('/') || value.contains('\\')) {
                TrackIdentity.fromLegacyUri(value)
            } else {
                safeToken(value, "unknown")
            }
        }

        private fun StringBuilder.appendLatest(latest: JSONObject) {
            appendLine("phase=${latest.optString("phase", "unknown")}")
            appendLine("pauseReason=${latest.optString("pauseReason", "unknown")}")
            appendLine("stopReason=${latest.optString("stopReason", "unknown")}")
            appendLine("queueSize=${latest.optInt("queueSize", 0)}")
            appendLine("currentIndex=${latest.optInt("currentIndex", -1)}")
            appendLine("mediaId=${latest.optString("mediaId", "none")}")
            appendLine("lastEvent=${latest.optString("type", "unknown")}")
            appendLine("lastError=${latest.optString("errorCategory", "none")}")
            appendLine("audioFocus=${latest.optString("audioFocus", "unknown")}")
            appendLine("mediaSessionActive=${latest.optBoolean("mediaSessionActive", false)}")
            appendLine("foregroundActive=${latest.optBoolean("foregroundActive", false)}")
        }

        private fun parseEvents(encoded: String?): JSONArray =
            runCatching { JSONArray(encoded ?: "[]") }.getOrElse { JSONArray() }

        private fun safeToken(value: String?, fallback: String): String =
            unsafeToken.replace(value?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback, "_")
                .take(96)

        private fun safeDevice(value: String?): String =
            unsafeDevice.replace(value ?: "unknown", " ")

        @Suppress("DEPRECATION")
        private fun appVersion(context: Context): String = runCatching {
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "${info.versionName} ($versionCode)"
        }.getOrDefault("unknown")

        private fun utcTimestamp(): String = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US,
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}
