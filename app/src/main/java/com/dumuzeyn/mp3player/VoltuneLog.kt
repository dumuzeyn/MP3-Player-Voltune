package com.dumuzeyn.mp3player

import android.util.Log

/** Debug-only logging that never records URIs, paths, titles, or exception messages. */
object VoltuneLog {
    private const val TAG = "VoltuneDebug"

    @JvmStatic
    fun info(event: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, event)
    }

    @JvmStatic
    fun warning(event: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, event)
    }

    @JvmStatic
    fun failure(event: String, error: Throwable?) {
        if (BuildConfig.DEBUG) {
            val category = error?.javaClass?.simpleName ?: "unknown"
            Log.e(TAG, "$event category=$category")
        }
    }
}
