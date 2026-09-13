package com.dumuzeyn.mp3player

import android.net.Uri

object TrackDeletionPolicy {
    @JvmStatic
    fun isMediaStore(uri: Uri?): Boolean = uri != null && isMediaStore(uri.toString())

    @JvmStatic
    fun isContentUri(uri: Uri?): Boolean = uri != null && isContentUri(uri.toString())

    @JvmStatic
    fun isMediaStore(uri: String?): Boolean = uri?.startsWith("content://media/") == true

    @JvmStatic
    fun isContentUri(uri: String?): Boolean = uri?.startsWith("content://") == true
}
