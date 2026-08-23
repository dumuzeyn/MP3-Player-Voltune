package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;
import java.util.Collections;
import org.junit.Test;

public class PlaybackUiStateTest {
    @Test
    public void projectsCurrentTrackAndPlaybackWithoutOwningMedia3() {
        LibraryState library = new LibraryState();
        Track track = new Track("content://song/1", "Song", "Artist");
        library.tracks.add(track);
        PlaybackUiState state = new PlaybackUiState();
        String mediaId = new MediaItemMapper().mediaId(track);

        state.updateSnapshot(new PlaybackSnapshot(
                Collections.singletonList(mediaId), mediaId, 0,
                12_000L, 60_000L, true, Player.STATE_READY,
                Player.REPEAT_MODE_ALL, false, PlaybackPhase.READY,
                PauseReason.NONE, StopReason.NONE, null, 1L));

        assertEquals(0, state.currentTrackIndex(library));
        assertTrue(state.isPlaying());
        assertEquals(2, state.repeatMode());

        state.updateSnapshot(PlaybackSnapshot.empty());
        assertFalse(state.isPlaying());
        assertSame(track, library.tracks.get(0));
    }
}
