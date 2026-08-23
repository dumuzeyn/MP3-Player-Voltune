package com.dumuzeyn.mp3player;

import java.util.Locale;

/** Conservative policy for media that can be added without an explicit user selection. */
final class DeviceAudioClassifier {
    private static final long MIN_MUSIC_DURATION_MS = 20_000L;
    private static final String[] VOICE_PATH_MARKERS = {
            "/recordings/", "/voice recorder/", "/voicerecorder/", "/sound_recorder/",
            "/call recordings/", "/callrecordings/", "/call_rec/", "/voice notes/",
            "/voicenotes/", "/whatsapp voice notes/", "/диктофон/"
    };
    private static final String[] VOICE_TEXT_MARKERS = {
            "voice recording", "voice record", "voice note", "audio recording",
            "call recording", "recorded call", "диктофон", "голосовая запись",
            "голосовое сообщение", "запись звонка"
    };

    private DeviceAudioClassifier() {
    }

    static boolean shouldAutoImport(Candidate candidate) {
        if (candidate == null || !candidate.music || candidate.recording
                || candidate.podcast || candidate.audiobook || candidate.ringtone
                || candidate.alarm || candidate.notification
                || candidate.durationMs < MIN_MUSIC_DURATION_MS) {
            return false;
        }
        String path = normalizedPath(candidate.relativePath);
        for (String marker : VOICE_PATH_MARKERS) {
            if (path.contains(marker)) {
                return false;
            }
        }
        String metadata = normalize(candidate.displayName) + ' ' + normalize(candidate.title)
                + ' ' + normalize(candidate.artist) + ' ' + normalize(candidate.album);
        for (String marker : VOICE_TEXT_MARKERS) {
            if (metadata.contains(marker)) {
                return false;
            }
        }
        if (looksLikeGeneratedVoiceMessage(candidate, path)) {
            return false;
        }
        return true;
    }

    private static boolean looksLikeGeneratedVoiceMessage(Candidate candidate, String path) {
        String file = normalize(candidate.displayName);
        if (file.matches("ptt-\\d{8}-wa\\d+\\.(opus|ogg|m4a|mp3)")) {
            return true;
        }
        boolean metadataUnknown = isUnknown(candidate.artist) && isUnknown(candidate.album);
        boolean generatedWhatsAppMetadata = isUnknown(candidate.artist)
                && "whatsapp audio".equals(normalize(candidate.album));
        if ((metadataUnknown || generatedWhatsAppMetadata) && path.contains("/whatsapp audio/")
                && file.matches("aud-\\d{8}-wa\\d+\\.(opus|ogg|m4a|mp3)")) {
            return true;
        }
        return metadataUnknown && (file.startsWith("voice_")
                || file.startsWith("voice-") || file.startsWith("recording_")
                || file.startsWith("recording-") || file.startsWith("call_record"));
    }

    private static boolean isUnknown(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() || "<unknown>".equals(normalized)
                || "unknown artist".equals(normalized)
                || "unknown album".equals(normalized);
    }

    private static String normalizedPath(String value) {
        String normalized = normalize(value).replace('\\', '/');
        return '/' + normalized.replaceAll("/+", "/") + '/';
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    static final class Candidate {
        final boolean music;
        final boolean recording;
        final boolean podcast;
        final boolean audiobook;
        final boolean ringtone;
        final boolean alarm;
        final boolean notification;
        final long durationMs;
        final String relativePath;
        final String displayName;
        final String title;
        final String artist;
        final String album;

        Candidate(boolean music, boolean recording, boolean podcast, boolean audiobook,
                boolean ringtone, boolean alarm, boolean notification, long durationMs,
                String relativePath, String displayName, String title, String artist,
                String album) {
            this.music = music;
            this.recording = recording;
            this.podcast = podcast;
            this.audiobook = audiobook;
            this.ringtone = ringtone;
            this.alarm = alarm;
            this.notification = notification;
            this.durationMs = durationMs;
            this.relativePath = relativePath;
            this.displayName = displayName;
            this.title = title;
            this.artist = artist;
            this.album = album;
        }
    }
}
