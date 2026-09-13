package com.dumuzeyn.mp3player

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Creates opaque identities that never expose a content URI. */
object TrackIdentity {
    @JvmStatic
    fun create(): String = "track-${UUID.randomUUID()}"

    @JvmStatic
    fun fromLegacyUri(uri: String?): String {
        val value = uri.orEmpty()
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
            buildString(39) {
                append("legacy-")
                repeat(16) { append(String.format(Locale.ROOT, "%02x", digest[it])) }
            }
        }.getOrElse { "legacy-${Integer.toHexString(value.hashCode())}" }
    }
}
