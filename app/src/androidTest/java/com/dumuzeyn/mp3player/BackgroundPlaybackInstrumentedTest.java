package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public class BackgroundPlaybackInstrumentedTest {
    private static final long TRANSITION_TIMEOUT_MS = 30000L;

    private Context context;
    private Instrumentation instrumentation;
    private File waveFile;
    private File secondWaveFile;
    private Track firstTrack;
    private Track secondTrack;
    private Activity activity;
    private MediaController controller;

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
        controller = new MediaController.Builder(context, new SessionToken(context,
                new ComponentName(context, Media3PlayerService.class)))
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync().get(15, TimeUnit.SECONDS);
        stopPlayback();
        waveFile = InstrumentedTestSupport.createTestWave(
                context, "instrumented-playback-1.wav", 6);
        secondWaveFile = InstrumentedTestSupport.createTestWave(
                context, "instrumented-playback-2.wav", 3);
        firstTrack = new Track(Uri.fromFile(waveFile).toString(), "Instrumentation tone 1",
                "Voltune tests", "Compatibility", "Test", 6000);
        secondTrack = new Track(Uri.fromFile(secondWaveFile).toString(),
                "Instrumentation tone 2", "Voltune tests", "Compatibility", "Test", 3000);
        TrackStore.save(context, Arrays.asList(firstTrack, secondTrack));
    }

    @After
    public void tearDown() {
        stopPlayback();
        TrackStore.save(context, Collections.<Track>emptyList());
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
        }
        if (controller != null) {
            controllerAction(controller::release);
        }
        if (waveFile != null) {
            waveFile.delete();
        }
        if (secondWaveFile != null) {
            secondWaveFile.delete();
        }
    }

    @Test
    public void playbackContinuesAfterActivityIsClosedAndCanBePaused() {
        activity = launchMainActivity();
        startQueue(Collections.singletonList(firstTrack), Player.REPEAT_MODE_ONE);
        waitForPlayingUri("Media3 did not start playback", firstTrack.uri);

        instrumentation.runOnMainSync(activity::finish);
        activity = null;
        InstrumentedTestSupport.waitFor("Playback stopped when Activity closed",
                TRANSITION_TIMEOUT_MS, () -> controllerValue(controller::isPlaying));
        InstrumentedTestSupport.waitFor("Playback position did not advance",
                TRANSITION_TIMEOUT_MS, () -> controllerValue(
                        () -> controller.getCurrentPosition()) > 0);

        controllerAction(controller::pause);
        InstrumentedTestSupport.waitFor("Media3 did not pause", 5000L,
                () -> !controllerValue(controller::isPlaying));
        assertFalse(controllerValue(controller::isPlaying));
        assertTrue(controllerValue(controller::getMediaItemCount) > 0);
    }

    @Test
    public void repeatingPlaylistWithSleepTimerKeepsPlayingAfterTaskIsRemoved() {
        activity = launchMainActivity();
        startQueue(Arrays.asList(firstTrack, secondTrack), Player.REPEAT_MODE_ALL);
        waitForPlayingUri("First playlist track did not start", firstTrack.uri);
        Bundle timer = new Bundle();
        timer.putLong(Media3Commands.ARG_TIMER_MS, 20000L);
        controllerAction(() -> controller.sendCustomCommand(
                Media3Commands.TIMER_START_COMMAND, timer));

        instrumentation.runOnMainSync(activity::finishAndRemoveTask);
        activity = null;
        waitForPlayingUri("Playlist did not advance after task removal", secondTrack.uri);
        waitForPlayingUri("Repeating playlist did not wrap", firstTrack.uri);
        assertTrue(com.dumuzeyn.mp3player.playback.service.PlaybackSleepTimer
                .readEndsAt(context) > System.currentTimeMillis());
    }

    @Test
    public void repeatAllKeepsCyclingWhileActivityIsInBackground() {
        activity = launchMainActivity();
        startQueue(Arrays.asList(firstTrack, secondTrack), Player.REPEAT_MODE_ALL);
        waitForPlayingUri("First repeat track did not start", firstTrack.uri);
        instrumentation.runOnMainSync(() -> activity.moveTaskToBack(true));
        waitForPlayingUri("Queue did not reach second track", secondTrack.uri);
        waitForPlayingUri("Queue did not wrap to first track", firstTrack.uri);
        assertTrue(controllerValue(controller::isPlaying));
        assertEquals(Player.REPEAT_MODE_ALL,
                (int) controllerValue(controller::getRepeatMode));
    }

    @Test
    public void controllerCanReconnectWithoutLosingQueueOrPosition() throws Exception {
        startQueue(Arrays.asList(firstTrack, secondTrack), Player.REPEAT_MODE_ALL);
        waitForPlayingUri("First reconnect track did not start", firstTrack.uri);
        InstrumentedTestSupport.waitFor("Playback position did not advance before reconnect",
                5000L, () -> controllerValue(() -> controller.getCurrentPosition()) > 250L);
        long positionBeforeReconnect = controllerValue(controller::getCurrentPosition);
        controllerAction(controller::release);
        controller = new MediaController.Builder(context, new SessionToken(context,
                new ComponentName(context, Media3PlayerService.class)))
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync().get(15, TimeUnit.SECONDS);
        assertEquals(2, (int) controllerValue(controller::getMediaItemCount));
        assertEquals(Player.REPEAT_MODE_ALL,
                (int) controllerValue(controller::getRepeatMode));
        assertTrue(controllerValue(controller::getCurrentPosition) >= positionBeforeReconnect);
        assertTrue(controllerValue(controller::isPlaying));
    }

    @Test
    public void unavailableUriDoesNotLoopForeverAndAdvancesToValidTrack() {
        Track unavailable = new Track("content://voltune.invalid/missing.mp3", "Missing",
                "Voltune tests", "Compatibility", "Test", 1000);
        startQueue(Arrays.asList(unavailable, secondTrack), Player.REPEAT_MODE_OFF);
        waitForPlayingUri("Media3 did not recover from unavailable URI", secondTrack.uri);
        assertTrue(controllerValue(controller::isPlaying));
    }

    @Test
    public void closingActivityWithFullPlayerDoesNotUseClosedCoverLoader() {
        activity = launchMainActivity();
        MainActivityCore host = (MainActivityCore) activity;
        instrumentation.runOnMainSync(() -> {
            setUiSnapshot(host, 0, false);
            host.playerUiController.openFullPlayer();
            activity.finish();
        });
        activity = null;
        SystemClock.sleep(1200L);
        assertEquals(0, (int) controllerValue(controller::getMediaItemCount));
    }

    @Test
    public void rotatingCoverResetsAndRestartsWhenTrackChanges() {
        activity = launchMainActivity();
        MainActivityCore host = (MainActivityCore) activity;
        RotatingCoverImageView[] holder = new RotatingCoverImageView[1];
        instrumentation.runOnMainSync(() -> {
            host.appearanceState.animations = true;
            host.appearanceState.circularCovers = true;
            setUiSnapshot(host, 0, true);
            holder[0] = new RotatingCoverImageView(host);
            host.root.addView(holder[0], new android.widget.FrameLayout.LayoutParams(200, 200));
            holder[0].bindTrack(host.libraryState.tracks.get(0));
        });
        InstrumentedTestSupport.waitFor("First cover did not rotate", 3000L,
                () -> holder[0].getRotation() > 1.0f);
        float[] reset = new float[1];
        instrumentation.runOnMainSync(() -> {
            setUiSnapshot(host, 1, true);
            holder[0].bindTrack(host.libraryState.tracks.get(1));
            reset[0] = holder[0].getRotation();
        });
        assertTrue(Math.abs(reset[0]) < 1.0f);
        InstrumentedTestSupport.waitFor("Second cover did not rotate", 3000L,
                () -> holder[0].getRotation() > 1.0f);
    }

    private void startQueue(List<Track> tracks, int repeatMode) {
        MediaItemMapper mapper = new MediaItemMapper();
        ArrayList<MediaItem> items = new ArrayList<>();
        for (Track track : tracks) {
            items.add(mapper.toMediaItem(track));
        }
        controllerAction(() -> {
            controller.setMediaItems(items);
            controller.setShuffleModeEnabled(false);
            controller.setRepeatMode(repeatMode);
            controller.prepare();
            controller.play();
        });
    }

    private static void setUiSnapshot(MainActivityCore host, int trackIndex, boolean playing) {
        Track track = host.libraryState.tracks.get(trackIndex);
        String mediaId = MediaItemMapper.stableHash(track.uri);
        host.updatePlaybackSnapshot(new PlaybackSnapshot(
                Collections.singletonList(mediaId), mediaId, 0, 0L, track.durationMs,
                playing, Player.STATE_READY, Player.REPEAT_MODE_OFF, false,
                PlaybackPhase.READY, PauseReason.NONE, StopReason.NONE,
                null, System.currentTimeMillis()));
    }

    private Activity launchMainActivity() {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Activity launched = monitor.waitForActivityWithTimeout(15000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("MainActivity did not start", launched);
        InstrumentedTestSupport.waitFor("MainActivity did not finish layout", 10000L,
                () -> ((MainActivityCore) launched).root != null
                        && ((MainActivityCore) launched).root.getWidth() > 0);
        return launched;
    }

    private void waitForPlayingUri(String message, String uri) {
        InstrumentedTestSupport.waitFor(message, TRANSITION_TIMEOUT_MS, () -> {
            return controllerValue(() -> {
                MediaItem current = controller.getCurrentMediaItem();
                return controller.isPlaying() && current != null
                        && current.localConfiguration != null
                        && uri.equals(current.localConfiguration.uri.toString());
            });
        });
    }

    private void stopPlayback() {
        if (controller == null) {
            return;
        }
        controllerAction(() -> {
            controller.stop();
            controller.clearMediaItems();
            controller.sendCustomCommand(Media3Commands.CLEAR_QUEUE_COMMAND, Bundle.EMPTY);
        });
        InstrumentedTestSupport.waitFor("Media3 session did not clear", 3000L,
                () -> controllerValue(controller::getMediaItemCount) == 0);
    }

    private void controllerAction(Runnable action) {
        instrumentation.runOnMainSync(action);
    }

    private <T> T controllerValue(Callable<T> query) {
        FutureTask<T> task = new FutureTask<>(query);
        instrumentation.runOnMainSync(task);
        try {
            return task.get(5, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new AssertionError("MediaController query failed", error);
        }
    }
}
