package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import org.junit.Test;

public class PlaybackHistoryTrackerTest {
    @Test
    public void reconnectLikeSamplesCountOnlyOnce() {
        ArrayList<String> events = new ArrayList<>();
        PlaybackHistoryTracker tracker = tracker(events);
        tracker.transitionTo("track", 180_000L, 1_000L, false);
        tracker.setPlaying(true, 1_000L);
        tracker.sample(16_000L);
        tracker.sample(31_000L);
        tracker.sample(46_000L);
        tracker.setPlaying(false, 47_000L);
        tracker.setPlaying(true, 48_000L);
        tracker.sample(60_000L);

        assertEquals(1, events.size());
        assertEquals("played:track:false", events.get(0));
    }

    @Test
    public void earlyManualTransitionRecordsSkip() {
        ArrayList<String> events = new ArrayList<>();
        PlaybackHistoryTracker tracker = tracker(events);
        tracker.transitionTo("first", 180_000L, 1_000L, false);
        tracker.setPlaying(true, 1_000L);
        tracker.sample(5_000L);
        tracker.transitionTo("second", 180_000L, 5_000L, true);

        assertEquals("skipped:first", events.get(0));
    }

    private PlaybackHistoryTracker tracker(ArrayList<String> events) {
        return new PlaybackHistoryTracker(new PlaybackHistoryTracker.Listener() {
            @Override
            public void onPlayed(String trackId, boolean completed, long timestamp) {
                events.add("played:" + trackId + ":" + completed);
            }

            @Override
            public void onSkipped(String trackId, long timestamp) {
                events.add("skipped:" + trackId);
            }
        });
    }
}
