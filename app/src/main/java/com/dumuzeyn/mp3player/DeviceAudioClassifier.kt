package com.dumuzeyn.mp3player

import java.util.Locale

/** Conservative policy for media that can be added without an explicit user selection. */
object DeviceAudioClassifier {
    private const val MIN_MUSIC_DURATION_MS = 20_000L
    private val voicePathMarkers = arrayOf(
        "/recordings/", "/voice recorder/", "/voicerecorder/", "/sound_recorder/",
        "/call recordings/", "/callrecordings/", "/call_rec/", "/voice notes/",
        "/voicenotes/", "/whatsapp voice notes/", "/диктофон/",
    )
    private val voiceTextMarkers = arrayOf(
        "voice recording", "voice record", "voice note", "audio recording",
        "call recording", "recorded call", "диктофон", "голосовая запись",
        "голосовое сообщение", "запись звонка",
    )
    private val pttPattern = Regex("ptt-\\d{8}-wa\\d+\\.(opus|ogg|m4a|mp3)")
    private val audioPattern = Regex("aud-\\d{8}-wa\\d+\\.(opus|ogg|m4a|mp3)")

    @JvmStatic
    fun shouldAutoImport(candidate: Candidate?): Boolean {
        if (
            candidate == null || !candidate.music || candidate.recording || candidate.podcast ||
            candidate.audiobook || candidate.ringtone || candidate.alarm || candidate.notification ||
            candidate.durationMs < MIN_MUSIC_DURATION_MS
        ) return false
        val path = normalizedPath(candidate.relativePath)
        if (voicePathMarkers.any(path::contains)) return false
        val metadata = listOf(
            candidate.displayName,
            candidate.title,
            candidate.artist,
            candidate.album,
        ).joinToString(" ", transform = ::normalize)
        if (voiceTextMarkers.any(metadata::contains)) return false
        return !looksLikeGeneratedVoiceMessage(candidate, path)
    }

    private fun looksLikeGeneratedVoiceMessage(candidate: Candidate, path: String): Boolean {
        val file = normalize(candidate.displayName)
        if (pttPattern.matches(file)) return true
        val metadataUnknown = isUnknown(candidate.artist) && isUnknown(candidate.album)
        val generatedWhatsAppMetadata = isUnknown(candidate.artist) &&
            normalize(candidate.album) == "whatsapp audio"
        if (
            (metadataUnknown || generatedWhatsAppMetadata) && "/whatsapp audio/" in path &&
            audioPattern.matches(file)
        ) return true
        return metadataUnknown && listOf(
            "voice_", "voice-", "recording_", "recording-", "call_record",
        ).any(file::startsWith)
    }

    private fun isUnknown(value: String?): Boolean = normalize(value).let {
        it.isEmpty() || it == "<unknown>" || it == "unknown artist" || it == "unknown album"
    }

    private fun normalizedPath(value: String?): String =
        "/" + normalize(value).replace('\\', '/').replace(Regex("/+"), "/") + "/"

    private fun normalize(value: String?): String = value?.lowercase(Locale.ROOT)?.trim().orEmpty()

    class Candidate(
        @JvmField val music: Boolean,
        @JvmField val recording: Boolean,
        @JvmField val podcast: Boolean,
        @JvmField val audiobook: Boolean,
        @JvmField val ringtone: Boolean,
        @JvmField val alarm: Boolean,
        @JvmField val notification: Boolean,
        @JvmField val durationMs: Long,
        @JvmField val relativePath: String?,
        @JvmField val displayName: String?,
        @JvmField val title: String?,
        @JvmField val artist: String?,
        @JvmField val album: String?,
    )
}
