package com.dumuzeyn.mp3player

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object CrashReportStore {
    private const val DIRECTORY = "crash-reports"
    private const val TAG = "MP3CrashReporter"
    private const val MAX_REPORTS = 5
    private const val MAX_REPORT_LENGTH = 96 * 1024

    @JvmStatic
    fun install(context: Context) {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is ReportingHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            ReportingHandler(context.applicationContext, current),
        )
    }

    @JvmStatic
    fun record(context: Context?, thread: Thread?, error: Throwable?): File? {
        if (context == null || error == null) return null
        val directory = reportDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "report_directory_failed")
            return null
        }
        val timestamp = utcTimestamp()
        val report = File(
            directory,
            "crash-${timestamp.replace(':', '-')}-${Process.myPid()}.txt",
        )
        val body = buildReport(context, thread, error, timestamp)
        return try {
            report.outputStream().use { stream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }
            prune(directory)
            Log.e(TAG, "crash_report_saved file=${report.name}")
            report
        } catch (writeError: Exception) {
            Log.e(TAG, "crash_report_write_failed", writeError)
            null
        }
    }

    @JvmStatic
    fun count(context: Context): Int = reports(context).size

    @JvmStatic
    fun latestSummary(context: Context): String {
        val latest = reports(context).firstOrNull() ?: return ""
        return try {
            latest.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.firstOrNull { it.startsWith("exception=") }
                    ?.substring("exception=".length)
            } ?: latest.name
        } catch (_: Exception) {
            latest.name
        }
    }

    @JvmStatic
    fun clear(context: Context) {
        reports(context).forEach { report ->
            if (!report.delete()) {
                Log.w(TAG, "crash_report_delete_failed file=${report.name}")
            }
        }
    }

    @JvmStatic
    fun sanitize(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return value
            .replace(Regex("content://\\S+"), "content://<redacted>")
            .replace(Regex("file:/+\\S+"), "file://<redacted>")
            .replace(Regex("/storage/\\S+"), "/storage/<redacted>")
            .replace(Regex("/sdcard/\\S+"), "/sdcard/<redacted>")
    }

    private fun buildReport(
        context: Context,
        thread: Thread?,
        error: Throwable,
        timestamp: String,
    ): String {
        val stackBuffer = StringWriter()
        error.printStackTrace(PrintWriter(stackBuffer))
        var stack = sanitize(stackBuffer.toString())
        if (stack.length > MAX_REPORT_LENGTH) {
            stack = stack.substring(0, MAX_REPORT_LENGTH) + "\n<truncated>"
        }
        return buildString {
            append("timestamp=").append(timestamp).append('\n')
            append("version=").append(appVersion(context)).append('\n')
            append("android=").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("device=").append(sanitize("${Build.MANUFACTURER} ${Build.MODEL}"))
                .append('\n')
            append("thread=").append(sanitize(thread?.name ?: "unknown")).append('\n')
            append("exception=")
                .append(sanitize("${error.javaClass.name}: ${error.message}"))
                .append("\n\n")
            append(stack)
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(context: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            "${info.versionName} (${info.versionCode})"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun reportDirectory(context: Context): File = File(context.filesDir, DIRECTORY)

    private fun reports(context: Context): Array<File> =
        reportDirectory(context).listFiles { _, name ->
            name.startsWith("crash-") && name.endsWith(".txt")
        }?.apply {
            sortWith(compareByDescending(File::lastModified))
        } ?: emptyArray()

    private fun prune(directory: File) {
        val files = directory.listFiles { _, name ->
            name.startsWith("crash-") && name.endsWith(".txt")
        } ?: return
        if (files.size <= MAX_REPORTS) return
        files.sortWith(compareByDescending(File::lastModified))
        for (index in MAX_REPORTS until files.size) {
            if (!files[index].delete()) {
                Log.w(TAG, "old_crash_report_delete_failed file=${files[index].name}")
            }
        }
    }

    private fun utcTimestamp(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US,
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    private class ReportingHandler(
        private val context: Context,
        private val next: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            record(context, thread, error)
            if (next != null) {
                next.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                System.exit(10)
            }
        }
    }
}
