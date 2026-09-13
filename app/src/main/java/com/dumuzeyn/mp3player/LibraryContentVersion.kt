package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences

/** Cheap cross-component signal that listening statistics changed in SQLite. */
object LibraryContentVersion {
    private const val PREFS = "library_content_version"
    private const val VALUE = "value"

    @JvmStatic
    fun read(context: Context): Long = preferences(context).getLong(VALUE, 0L)

    @JvmStatic
    @Synchronized
    fun bump(context: Context) {
        val preferences = preferences(context)
        val previous = preferences.getLong(VALUE, 0L)
        val next = maxOf(System.currentTimeMillis(), previous + 1L)
        preferences.edit().putLong(VALUE, next).apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
