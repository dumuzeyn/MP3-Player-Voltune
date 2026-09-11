package com.dumuzeyn.mp3player

import org.junit.Assert.*
import org.junit.Test

class AudioImportFormatsTest {
    @Test fun audioExtensionsAreRecognizedWithoutMimeMetadata() {
        for (extension in listOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac")) {
            assertTrue(extension, AudioImportController.hasAudioExtension("Track.$extension"))
            assertTrue(extension, AudioImportController.hasAudioExtension("Track.${extension.uppercase()}"))
        }
    }

    @Test fun unrelatedFilesAndDisguisedSuffixesAreRejected() {
        for (name in listOf(null, "", "track", "track.opus.exe", "photo.png", "audio.wma", "audio.mp3.txt")) {
            assertFalse(name, AudioImportController.hasAudioExtension(name))
        }
    }
}
