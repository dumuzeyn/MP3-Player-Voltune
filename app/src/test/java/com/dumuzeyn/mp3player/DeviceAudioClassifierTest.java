package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceAudioClassifierTest {
    @Test
    public void acceptsRegularMusic() {
        assertTrue(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Music/Albums/", "song.mp3", "Song", "Artist", "Album",
                true, false, 180_000L)));
    }

    @Test
    public void rejectsSystemRecordingFlag() {
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Music/", "track.m4a", "Track", "Artist", "Album",
                true, true, 180_000L)));
    }

    @Test
    public void rejectsVoiceAndCallRecordingFolders() {
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Recordings/Voice Recorder/", "recording.m4a", "Meeting", "", "",
                true, false, 600_000L)));
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "MIUI/sound_recorder/call_rec/", "call.mp3", "Call", "", "",
                true, false, 120_000L)));
    }

    @Test
    public void rejectsVoiceMetadataAndShortAudio() {
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Download/", "voice-note.ogg", "Voice note 12", "", "",
                true, false, 45_000L)));
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Music/", "clip.mp3", "Clip", "Artist", "Album",
                true, false, 8_000L)));
    }

    @Test
    public void rejectsGeneratedWhatsAppVoiceAndAudioNames() {
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202627/",
                "PTT-20260629-WA0000.opus", "PTT-20260629-WA0000", "", "",
                true, false, 45_000L)));
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/",
                "AUD-20260629-WA0004.opus", "AUD-20260629-WA0004", "",
                "WhatsApp Audio",
                true, false, 31_700L)));
    }

    @Test
    public void keepsNamedMusicSharedThroughMessenger() {
        assertTrue(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/",
                "My Song.mp3", "My Song", "Artist", "Album",
                true, false, 180_000L)));
    }

    @Test
    public void requiresMusicAndRejectsOtherAudioCategories() {
        assertFalse(DeviceAudioClassifier.shouldAutoImport(candidate(
                "Podcasts/", "episode.mp3", "Episode", "Host", "Show",
                false, false, 1_800_000L)));
        DeviceAudioClassifier.Candidate podcast = new DeviceAudioClassifier.Candidate(
                true, false, true, false, false, false, false, 1_800_000L,
                "Podcasts/", "episode.mp3", "Episode", "Host", "Show");
        assertFalse(DeviceAudioClassifier.shouldAutoImport(podcast));
    }

    private static DeviceAudioClassifier.Candidate candidate(String path, String file,
            String title, String artist, String album, boolean music, boolean recording,
            long durationMs) {
        return new DeviceAudioClassifier.Candidate(music, recording, false, false,
                false, false, false, durationMs, path, file, title, artist, album);
    }
}
