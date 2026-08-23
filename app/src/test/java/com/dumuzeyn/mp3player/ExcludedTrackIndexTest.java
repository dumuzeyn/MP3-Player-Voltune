package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

public class ExcludedTrackIndexTest {
    @Test
    public void exactSafDocumentIsExcludedWithoutUsingTitle() {
        Track removed = track("content://tree/document/music%3Aone.mp3", "Same title",
                "hash-one", 101L);
        ExcludedTrack exclusion = new ExcludedTrack("document:source:music:one.mp3",
                "source", "music:one.mp3", removed.uri, removed.fileSize,
                removed.lastModified, removed.fingerprint);
        ExcludedTrackIndex index = new ExcludedTrackIndex(Arrays.asList(exclusion));

        assertTrue(index.contains("document:source:music:one.mp3", removed));
        assertFalse(index.contains("document:source:music:two.mp3",
                track("content://tree/document/music%3Atwo.mp3", "Same title",
                        "hash-two", 202L)));
    }

    @Test
    public void sameContentCanBeRecognizedAfterUriChanges() {
        ExcludedTrack exclusion = new ExcludedTrack("uri:content://old", "", "",
                "content://old", 4096L, 10L, "stable-hash");
        ExcludedTrackIndex index = new ExcludedTrackIndex(Arrays.asList(exclusion));

        assertTrue(index.contains("uri:content://new",
                track("content://new", "Renamed", "stable-hash", 4096L)));
        assertFalse(index.contains("uri:content://different",
                track("content://different", "Same title", "stable-hash", 8192L)));
    }

    private static Track track(String uri, String title, String fingerprint, long size) {
        return new Track("id-" + uri, uri, title, "Artist", "Album", "Genre",
                120000, size, 42L, fingerprint);
    }
}
