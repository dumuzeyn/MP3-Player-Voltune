package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlaybackRemovalInstrumentedTest {
    private PlaybackStateManager state;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        state = new PlaybackStateManager(context);
        state.clear();
    }

    @After
    public void tearDown() {
        state.clear();
    }

    @Test
    public void removingCurrentTrackPersistsNextTrackAtZeroPosition() {
        save("content://A", 42000, 0);

        state.removeTracks(new HashSet<>(Collections.singletonList("A")),
                new HashSet<>(Collections.singletonList("content://A")));
        PlaybackStateManager.State restored = new PlaybackStateManager(
                ApplicationProvider.getApplicationContext()).load();

        assertEquals(Arrays.asList("B", "C"), restored.queueUris);
        assertEquals(0, restored.index);
        assertEquals(0, restored.position);
        assertEquals("", restored.uri);
    }

    @Test
    public void removingMiddleTrackKeepsCurrentTrackAndPosition() {
        save("content://A", 42000, 0);

        state.removeTracks(new HashSet<>(Collections.singletonList("B")),
                new HashSet<>(Collections.singletonList("content://B")));
        PlaybackStateManager.State restored = state.load();

        assertEquals(Arrays.asList("A", "C"), restored.queueUris);
        assertEquals(0, restored.index);
        assertEquals(42000, restored.position);
        assertEquals("content://A", restored.uri);
        assertFalse(restored.queueUris.contains("B"));
    }

    private void save(String currentUri, int position, int index) {
        state.save(new PlaybackStateManager.Snapshot(currentUri, position, 120000, index,
                2, true, false, new ArrayList<>(Arrays.asList("A", "B", "C"))), true);
    }
}
