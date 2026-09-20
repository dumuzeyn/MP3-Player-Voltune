package com.dumuzeyn.mp3player

internal enum class AudioExportFormat(val extension: String, val mimeType: String) {
    M4A("m4a", "audio/mp4"),
    MP3("mp3", "audio/mpeg"),
    WAV("wav", "audio/wav");

    companion object {
        fun fromFile(file: java.io.File): AudioExportFormat? = entries.firstOrNull {
            it.extension.equals(file.extension, ignoreCase = true)
        }
    }
}
