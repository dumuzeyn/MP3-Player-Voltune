package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.common.Player;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Proxy;

@RunWith(AndroidJUnit4.class)
public class MiniPlayerRetentionInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearPreferences();
    }

    @After
    public void tearDown() {
        clearPreferences();
    }

    @Test
    public void serviceDoesNotRestoreEightHourOldPauseWithTwoHourLimit() {
        long now = System.currentTimeMillis();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putInt("resumeWindowMinutes", 120)
                .commit();
        context.getSharedPreferences(PlaybackStateManager.PREFS, Context.MODE_PRIVATE).edit()
                .putString("uri", "content://stale-track")
                .putString("queue", "[\"stale-track\"]")
                .putBoolean("playing", false)
                .putLong("inactiveSince", now - 8L * 60L * 60L * 1000L)
                .putLong("savedAt", now)
                .commit();

        Player unusedPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    throw new AssertionError("Expired playback must not touch the player");
                });
        PlaybackStateManager stateManager = new PlaybackStateManager(context);
        new PlaybackSessionRestorer(context, stateManager, new MediaItemMapper())
                .restore(unusedPlayer);

        assertTrue(stateManager.load().queueUris.isEmpty());
    }

    @Test
    public void playbackItemCarriesLoudnessIdentityWithoutDatabaseLookup() {
        Track source = new Track("track-rich", "content://music/rich", "Song", "Artist",
                "Album", "Electronic", 123000, 4567L, 890L, "fingerprint");

        Track restored = new MediaItemMapper().fromMediaItem(
                new MediaItemMapper().toMediaItem(source));

        assertNotNull(restored);
        assertEquals(source.trackId, restored.trackId);
        assertEquals(source.uri, restored.uri);
        assertEquals(source.genre, restored.genre);
        assertEquals(source.durationMs, restored.durationMs);
        assertEquals(source.fileSize, restored.fileSize);
        assertEquals(source.lastModified, restored.lastModified);
        assertEquals(source.fingerprint, restored.fingerprint);
    }

    private void clearPreferences() {
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .clear().commit();
        context.getSharedPreferences(PlaybackStateManager.PREFS, Context.MODE_PRIVATE).edit()
                .clear().commit();
    }
}
