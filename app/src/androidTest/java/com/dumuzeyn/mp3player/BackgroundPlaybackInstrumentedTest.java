package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class BackgroundPlaybackInstrumentedTest {
    private static final long PLAYBACK_TRANSITION_TIMEOUT_MS = 30000L;

    private Context context;
    private Instrumentation instrumentation;
    private File waveFile;
    private File secondWaveFile;
    private Activity activity;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putBoolean("particlesEnabled", false)
                .putBoolean("animations", false)
                .commit();
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentedTestSupport.runShellCommand(instrumentation,
                    "pm grant " + context.getPackageName() + " "
                            + Manifest.permission.POST_NOTIFICATIONS);
        }
        stopPlayback();
        TrackStore.save(context, Collections.<Track>emptyList());
        waveFile = InstrumentedTestSupport.createTestWave(
                context, "instrumented-playback-1.wav", 6);
        secondWaveFile = InstrumentedTestSupport.createTestWave(
                context, "instrumented-playback-2.wav", 3);
        Track firstTrack = new Track(Uri.fromFile(waveFile).toString(), "Instrumentation tone 1",
                "MP3 Player Voltune tests", "Compatibility", "Test", 6000);
        Track secondTrack = new Track(Uri.fromFile(secondWaveFile).toString(), "Instrumentation tone 2",
                "MP3 Player Voltune tests", "Compatibility", "Test", 3000);
        TrackStore.save(context, Arrays.asList(firstTrack, secondTrack));
    }

    @After
    public void tearDown() {
        stopPlayback();
        TrackStore.save(context, Collections.<Track>emptyList());
        if (waveFile != null && waveFile.exists()) {
            waveFile.delete();
        }
        if (secondWaveFile != null && secondWaveFile.exists()) {
            secondWaveFile.delete();
        }
        if (activity != null) {
            instrumentation.runOnMainSync(() -> activity.finish());
        }
    }

    @Test
    public void playbackContinuesAfterActivityIsClosedAndCanBePaused() {
        activity = launchMainActivity();

        ArrayList<String> queue = new ArrayList<>();
        queue.add(Uri.fromFile(waveFile).toString());
        Intent playIntent = new Intent(context, PlayerService.class)
                .setAction(PlayerService.ACTION_PLAY_INDEX)
                .putExtra(PlayerService.EXTRA_INDEX, 0)
                .putStringArrayListExtra(PlayerService.EXTRA_QUEUE_URIS, queue)
                .putExtra(PlayerService.EXTRA_SHUFFLE, false)
                .putExtra(PlayerService.EXTRA_LOOP_MODE, 1);
        startPlaybackService(playIntent);

        InstrumentedTestSupport.waitFor("PlayerService did not start playback", 15000L,
                () -> PlayerService.lastPlaying && PlayerService.lastDuration > 0);
        assertEquals(queue.get(0), PlayerService.lastUri);

        instrumentation.runOnMainSync(() -> activity.finish());
        activity = null;
        InstrumentedTestSupport.waitFor(
                "Playback stopped when Activity closed", PLAYBACK_TRANSITION_TIMEOUT_MS,
                () -> {
                    PlayerService.refreshSnapshot();
                    return PlayerService.lastPlaying;
                });
        InstrumentedTestSupport.waitFor(
                "Playback position did not advance", PLAYBACK_TRANSITION_TIMEOUT_MS,
                () -> {
                    PlayerService.refreshSnapshot();
                    return PlayerService.lastPlaying && PlayerService.lastPosition > 0;
                });

        Intent pauseIntent = new Intent(context, PlayerService.class)
                .setAction(PlayerService.ACTION_TOGGLE);
        context.startService(pauseIntent);
        InstrumentedTestSupport.waitFor("PlayerService did not pause", 5000L,
                () -> {
                    PlayerService.refreshSnapshot();
                    return !PlayerService.lastPlaying;
                });
        assertFalse(PlayerService.lastPlaying);
        assertTrue(PlayerService.hasPlaybackSession());
    }

    @Test
    public void repeatingPlaylistWithSleepTimerKeepsPlayingAfterTaskIsRemoved() {
        activity = launchMainActivity();

        ArrayList<String> queue = new ArrayList<>();
        queue.add(Uri.fromFile(waveFile).toString());
        queue.add(Uri.fromFile(secondWaveFile).toString());
        Intent playIntent = new Intent(context, PlayerService.class)
                .setAction(PlayerService.ACTION_PLAY_INDEX)
                .putExtra(PlayerService.EXTRA_INDEX, 0)
                .putExtra(PlayerService.EXTRA_ONE_SHOT, false)
                .putStringArrayListExtra(PlayerService.EXTRA_QUEUE_URIS, queue)
                .putExtra(PlayerService.EXTRA_SHUFFLE, false)
                .putExtra(PlayerService.EXTRA_LOOP_MODE, 2);
        startPlaybackService(playIntent);

        InstrumentedTestSupport.waitFor("First playlist track did not start", 10000L,
                () -> PlayerService.lastPlaying && queue.get(0).equals(PlayerService.lastUri));
        startPlaybackService(new Intent(context, PlayerService.class)
                .setAction(PlayerService.ACTION_TIMER_START)
                .putExtra(PlayerService.EXTRA_TIMER_MS, 20000L));

        instrumentation.runOnMainSync(() -> activity.finishAndRemoveTask());
        activity = null;

        InstrumentedTestSupport.waitFor(
                "Playlist did not advance after the task was removed",
                PLAYBACK_TRANSITION_TIMEOUT_MS,
                () -> PlayerService.lastPlaying && queue.get(1).equals(PlayerService.lastUri));
        InstrumentedTestSupport.waitFor(
                "Repeating playlist did not wrap after the task was removed",
                PLAYBACK_TRANSITION_TIMEOUT_MS,
                () -> PlayerService.lastPlaying && queue.get(0).equals(PlayerService.lastUri));
        assertTrue(PlayerService.getSleepTimerEndsAt(context) > System.currentTimeMillis());
    }

    @Test
    public void repeatAllKeepsCyclingWhileActivityIsInBackground() {
        activity = launchMainActivity();

        ArrayList<String> queue = new ArrayList<>();
        queue.add(Uri.fromFile(waveFile).toString());
        queue.add(Uri.fromFile(secondWaveFile).toString());
        startPlaybackService(new Intent(context, PlayerService.class)
                .setAction(PlayerService.ACTION_PLAY_INDEX)
                .putExtra(PlayerService.EXTRA_INDEX, 0)
                .putExtra(PlayerService.EXTRA_ONE_SHOT, false)
                .putStringArrayListExtra(PlayerService.EXTRA_QUEUE_URIS, queue)
                .putExtra(PlayerService.EXTRA_SHUFFLE, false)
                .putExtra(PlayerService.EXTRA_LOOP_MODE, 2));

        waitForPlayingUri("First repeat track did not start", queue.get(0));
        instrumentation.runOnMainSync(() -> activity.moveTaskToBack(true));

        waitForPlayingUri("Repeat queue did not reach the second track", queue.get(1));
        waitForPlayingUri("Repeat queue did not wrap to the first track", queue.get(0));
        waitForPlayingUri("Repeat queue stopped during its second cycle", queue.get(1));
        assertTrue(PlayerService.hasPlaybackSession());
        assertTrue(PlayerService.lastPlaying);
        assertEquals(2, PlayerService.lastLoopMode);
    }

    @Test
    public void closingActivityWithFullPlayerDoesNotUseClosedCoverLoader() {
        activity = launchMainActivity();

        MainActivityCore host = (MainActivityCore) activity;
        instrumentation.runOnMainSync(() -> {
            host.currentIndex = 0;
            host.openFullPlayer();
            activity.finish();
        });
        activity = null;
        SystemClock.sleep(1200L);
        assertFalse(PlayerService.hasPlaybackSession());
    }

    @Test
    public void rotatingCoverResetsAndRestartsWhenTrackChanges() {
        activity = launchMainActivity();

        MainActivityCore host = (MainActivityCore) activity;
        RotatingCoverImageView[] coverHolder = new RotatingCoverImageView[1];
        instrumentation.runOnMainSync(() -> {
            host.animations = true;
            host.circularCovers = true;
            host.currentIndex = 0;
            host.playing = true;
            RotatingCoverImageView cover = new RotatingCoverImageView(host);
            coverHolder[0] = cover;
            host.root.addView(cover, new android.widget.FrameLayout.LayoutParams(200, 200));
            cover.bindTrack(host.tracks.get(0));
        });

        InstrumentedTestSupport.waitFor("First cover did not rotate", 3000L,
                () -> coverHolder[0].getRotation() > 1.0f);
        float[] resetRotation = new float[1];
        instrumentation.runOnMainSync(() -> {
            host.currentIndex = 1;
            coverHolder[0].bindTrack(host.tracks.get(1));
            resetRotation[0] = coverHolder[0].getRotation();
        });
        assertTrue("New track cover did not reset to its initial angle",
                Math.abs(resetRotation[0]) < 1.0f);
        InstrumentedTestSupport.waitFor("Second cover did not start rotating", 3000L,
                () -> coverHolder[0].getRotation() > 1.0f);
    }

    private Activity launchMainActivity() {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        Intent activityIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);
        Activity launched = monitor.waitForActivityWithTimeout(45000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("MainActivity did not start", launched);
        InstrumentedTestSupport.waitFor("MainActivity did not finish its first layout", 10000L,
                () -> {
                    MainActivityCore host = (MainActivityCore) launched;
                    return host.root != null && host.root.getWidth() > 0;
                });
        return launched;
    }

    private void startPlaybackService(Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void waitForPlayingUri(String message, String uri) {
        InstrumentedTestSupport.waitFor(message, PLAYBACK_TRANSITION_TIMEOUT_MS, () -> {
            PlayerService.refreshSnapshot();
            return PlayerService.lastPlaying && uri.equals(PlayerService.lastUri);
        });
    }

    private void stopPlayback() {
        try {
            Intent stopIntent = new Intent(context, PlayerService.class)
                    .setAction(PlayerService.ACTION_STOP);
            startPlaybackService(stopIntent);
        } catch (RuntimeException ignored) {
        }
        InstrumentedTestSupport.waitFor("PlayerService did not stop", 3000L,
                () -> !PlayerService.hasPlaybackSession());
        SystemClock.sleep(250L);
    }
}
