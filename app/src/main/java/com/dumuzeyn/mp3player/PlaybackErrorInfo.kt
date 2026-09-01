package com.dumuzeyn.mp3player

class PlaybackErrorInfo(
    @JvmField val code: Int,
    category: String?,
    @JvmField val recoverable: Boolean,
    safeMessage: String?,
    mediaId: String?,
) {
    @JvmField val category: String = sanitize(category, "unknown")
    @JvmField val safeMessage: String = sanitize(safeMessage, "Playback error")
    @JvmField val mediaId: String = sanitize(mediaId, "")

    private companion object {
        private val mediaLocation = Regex("(?i)(content|file)://\\S+")

        fun sanitize(value: String?, fallback: String): String {
            val normalized = value?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
            return mediaLocation.replace(normalized, "[media]").take(160)
        }
    }
}
