package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TrackLoudnessNormalizerInstrumentedTest {
    private Context context;
    private SharedPreferences cache;
    private TrackLoudnessNormalizer normalizer;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        cache = context.getSharedPreferences(TrackLoudnessNormalizer.PREFS, 0);
        cache.edit().clear().commit();
        context.getSharedPreferences(EqualizerController.PREFS, 0).edit()
                .putBoolean(VolumeLevelingController.ENABLED, true).commit();
        normalizer = new TrackLoudnessNormalizer(context);
    }

    @After
    public void tearDown() {
        normalizer.release();
        cache.edit().clear().commit();
    }

    @Test
    public void corruptCacheEntryIsIgnoredAndClearRemovesAllResults() {
        Track track = track("track-corrupt", "content://audio/corrupt");
        String resultKey = "r128_result_" + TrackLoudnessNormalizer.cacheKeyFor(track);
        cache.edit().putString(resultKey, "not-a-result")
                .putString("r128_error_" + track.trackId, "decode_error").commit();

        assertEquals(0.0f, normalizer.cachedGainDb(track), 0.001f);
        assertFalse(cache.contains(resultKey));
        normalizer.clearCache();
        assertTrue(cache.getAll().isEmpty());
    }

    @Test
    public void batchCanBeCancelled() throws Exception {
        ArrayList<Track> tracks = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            tracks.add(track("track-" + index, "content://missing/" + index));
        }
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        normalizer.analyzeLibrary(tracks, (completed, total, errors, done, wasCancelled) -> {
            if (done) {
                cancelled.set(wasCancelled);
                finished.countDown();
            }
        });
        normalizer.cancelAnalysis();

        assertTrue(finished.await(3, TimeUnit.SECONDS));
        assertTrue(cancelled.get());
    }

    @Test
    public void unreadableFileIsReportedPerTrack() throws Exception {
        Track track = track("track-missing", "content://missing/audio");
        CountDownLatch finished = new CountDownLatch(1);
        normalizer.analyzeLibrary(Collections.singletonList(track),
                (completed, total, errors, done, cancelled) -> {
                    if (done) {
                        finished.countDown();
                    }
                });

        assertTrue(finished.await(3, TimeUnit.SECONDS));
        assertTrue(normalizer.failedTrackIds().contains(track.trackId));
    }

    private static Track track(String id, String uri) {
        return new Track(id, uri, "Title", "Artist", "Album", "Genre", 1000,
                100L, 200L, "fingerprint");
    }
}
