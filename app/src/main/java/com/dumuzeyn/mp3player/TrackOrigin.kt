package com.dumuzeyn.mp3player

/** Stable ownership data for a track discovered inside a SAF tree. */
class TrackOrigin(
    @JvmField val trackId: String,
    @JvmField val sourceId: String,
    @JvmField val documentId: String,
    @JvmField val identityKey: String,
) {
    companion object {
        @JvmStatic
        fun identity(sourceId: String, documentId: String): String =
            "document:$sourceId:$documentId"

        @JvmStatic
        fun uriIdentity(uri: String?): String = "uri:${uri.orEmpty()}"
    }
}
