package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public class HomeContentBuilderTest {
    @Test
    public void buildsReusableGroupsAndCompleteFavorites() {
        Track rock = new Track("content://music/rock", "Rock song", "Artist", "Album",
                "(17)");
        Track custom = new Track("content://music/custom", "Custom song", "Artist", "Album 2",
                "Dark Folk / Neo Folk");
        Track unknown = new Track("content://music/unknown", "Unknown", "Unknown artist",
                "Unknown album", "unknown");
        HashSet<String> favorites = new HashSet<>(
                Arrays.asList(rock.uri, custom.uri, unknown.uri));

        HomeContent content = new HomeContentBuilder().build(
                Arrays.asList(rock, custom, unknown), favorites, Collections.emptyList());

        assertEquals(3, content.allFavorites.size());
        assertEquals(2, content.artistTracks.get("Artist").size());
        assertEquals(1, content.genreTracks.get("Rock").size());
        assertEquals(1, content.genreTracks.get("Dark Folk / Neo Folk").size());
        assertEquals(1, content.genreTracks.get("").size());
        assertTrue(content.artistTracks.containsKey(""));
    }
}
