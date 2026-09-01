package com.dumuzeyn.mp3player

import android.net.Uri
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** A persisted SAF tree that owns imported library tracks. */
class LibrarySource(
    @JvmField val sourceId: String,
    @JvmField val treeUri: String,
    @JvmField val displayName: String,
    @JvmField val revision: Long,
) {
    fun asUri(): Uri = Uri.parse(treeUri)

    companion object {
        @JvmStatic
        fun idFor(treeUri: Uri?): String {
            val value = treeUri?.normalizeScheme()?.toString().orEmpty()
            return runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toByteArray(StandardCharsets.UTF_8))
                buildString(68) {
                    append("saf:")
                    digest.forEach { append(String.format(Locale.ROOT, "%02x", it)) }
                }
            }.getOrElse { "saf:${Integer.toHexString(value.hashCode())}" }
        }
    }
}
