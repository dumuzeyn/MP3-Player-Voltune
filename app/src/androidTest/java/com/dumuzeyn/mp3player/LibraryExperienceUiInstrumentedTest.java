package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LibraryExperienceUiInstrumentedTest {
    private Instrumentation instrumentation;
    private Activity activity;

    @After
    public void tearDown() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    @Test
    public void homeSearchQueueLyricsMetadataFavoritesPlaylistsAndSettingsOpen() {
        MainActivityCore host = launchWithLibrary();
        assertEquals(LibraryTabs.HOME, host.navigationState.tabIndex);
        Track track = host.libraryState.tracks.get(0);

        assertOverlayOpens(host, host.overlayController::openSearch);
        assertOverlayOpens(host, host.overlayController::openQueue);
        assertOverlayOpens(host, () -> host.lyricsOverlayController.open(track));
        assertOverlayOpens(host, () -> host.metadataEditorController.open(track));

        instrumentation.runOnMainSync(() -> {
            host.toggleFavorite(track);
            Playlist playlist = host.playlistController.createPlaylist("UI smoke");
            host.playlistController.addTrackToPlaylist(playlist, track);
            host.switchTabAnimated(LibraryTabs.SONGS, 1);
        });
        InstrumentedTestSupport.waitFor("Songs tab did not open", 5000L,
                () -> host.navigationState.tabIndex == LibraryTabs.SONGS);
        assertTrue(host.libraryState.favorites.contains(track.uri));
        assertFalse(host.libraryState.playlists.isEmpty());

        instrumentation.runOnMainSync(() ->
                host.switchTabAnimated(LibraryTabs.SETTINGS, 1));
        InstrumentedTestSupport.waitFor("Settings tab did not open", 5000L,
                () -> host.navigationState.tabIndex == LibraryTabs.SETTINGS);
    }

    private MainActivityCore launchWithLibrary() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        context.getSharedPreferences("mp3_player_store", Context.MODE_PRIVATE).edit()
                .putBoolean("sqlite_migrated", true)
                .commit();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putBoolean("animations", false)
                .putBoolean("particlesEnabled", false)
                .commit();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 10)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        activity = monitor.waitForActivityWithTimeout(15000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull(activity);
        MainActivityCore host = (MainActivityCore) activity;
        InstrumentedTestSupport.waitFor("Benchmark library did not load", 10000L,
                () -> host.libraryState.tracks.size() >= 10 && host.root != null);
        return host;
    }

    private void assertOverlayOpens(MainActivityCore host, Runnable action) {
        instrumentation.runOnMainSync(() -> {
            host.overlayHost.removeAllViews();
            action.run();
        });
        InstrumentedTestSupport.waitFor("Overlay did not open", 5000L,
                () -> host.overlayHost.getChildCount() > 0);
        instrumentation.runOnMainSync(host.overlayHost::removeAllViews);
    }
}
