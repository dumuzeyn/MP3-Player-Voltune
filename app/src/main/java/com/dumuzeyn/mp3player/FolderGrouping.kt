package com.dumuzeyn.mp3player

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class FolderGrouping {
    fun group(tracks: List<Track>): Map<String, ArrayList<Track>> {
        val groups = LinkedHashMap<String, ArrayList<Track>>()
        tracks.forEach { track -> groups.getOrPut(folderName(track.uri), ::ArrayList) += track }
        return groups
    }

    companion object {
        private const val UNKNOWN = "Unknown folder"

        @JvmStatic
        fun folderName(rawUri: String?): String = runCatching {
            if (rawUri.isNullOrBlank()) return UNKNOWN
            var path = rawUri.substringBefore('?')
            val scheme = path.indexOf("://")
            if (scheme >= 0) {
                val firstPath = path.indexOf('/', scheme + 3)
                path = if (firstPath >= 0) path.substring(firstPath + 1) else ""
            }
            path = URLDecoder.decode(path, StandardCharsets.UTF_8.name())
            if (path.isBlank()) return UNKNOWN
            val separator = maxOf(path.lastIndexOf('/'), path.lastIndexOf(':'))
            val parent = if (separator > 0) path.substring(0, separator) else path
            val parentSeparator = maxOf(parent.lastIndexOf('/'), parent.lastIndexOf(':'))
            parent.substring(parentSeparator + 1).trim().ifEmpty { UNKNOWN }
        }.getOrDefault(UNKNOWN)
    }
}
