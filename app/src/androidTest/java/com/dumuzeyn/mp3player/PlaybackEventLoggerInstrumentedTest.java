package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlaybackEventLoggerInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PlaybackEventLogger.clear(context);
    }

    @After
    public void tearDown() {
        PlaybackEventLogger.clear(context);
    }

    @Test
    public void reportIsBoundedAndDoesNotExposeMediaUri() {
        PlaybackEventLogger logger = new PlaybackEventLogger(context);
        PlaybackSnapshot snapshot = new PlaybackSnapshot(Collections.emptyList(),
                "content://media/private/favorite-song.mp3", 2, 1000L, 2000L, true,
                3, 0, false, PlaybackPhase.READY, PauseReason.NONE, StopReason.NONE,
                null, System.currentTimeMillis());
        for (int index = 0; index < 205; index++) {
            logger.record("test_event", snapshot, "none", "active", true, true);
        }

        String report = PlaybackEventLogger.buildReport(context);

        assertTrue(report.contains("eventCount=200"));
        assertFalse(report.contains("content://"));
        assertFalse(report.contains("favorite-song"));
    }
}
