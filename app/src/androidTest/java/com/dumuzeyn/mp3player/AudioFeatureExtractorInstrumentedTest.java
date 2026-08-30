package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AudioFeatureExtractorInstrumentedTest {
    @Test
    public void decodesWaveAsBoundedFeatureVector() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File wave = InstrumentedTestSupport.createTestWave(
                context, "sound-analysis.wav", 3);
        Track track = new Track("sound-wave", Uri.fromFile(wave).toString(),
                "Wave", "Voltune", "Tests", "Synthetic", 3000,
                wave.length(), wave.lastModified(), "sound-wave");

        double[] features = new AudioFeatureExtractor(context).analyze(track, () -> false);

        assertEquals(TrackAudioProfile.FEATURE_COUNT, features.length);
        assertTrue(features[TrackAudioProfile.LOUDNESS] < 0.0d);
        assertTrue(features[TrackAudioProfile.CENTROID] > 0.0d);
    }

    @Test
    public void cancellationStopsBeforeDecode() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File wave = InstrumentedTestSupport.createTestWave(
                context, "sound-analysis-cancel.wav", 1);
        Track track = new Track("sound-cancel", Uri.fromFile(wave).toString(),
                "Wave", "Voltune", "Tests", "Synthetic", 1000,
                wave.length(), wave.lastModified(), "sound-cancel");
        try {
            new AudioFeatureExtractor(context).analyze(track, () -> true);
            fail("Analysis should have stopped");
        } catch (AudioFeatureExtractor.AnalysisInterruptedException expected) {
            // Expected cooperative cancellation.
        }
    }
}
