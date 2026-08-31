package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.Collections;
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
        assertFullPlayerPages(host, track);
        assertOverlayOpens(host, () -> host.metadataEditorController.open(track));

        instrumentation.runOnMainSync(() -> {
            host.toggleFavorite(track);
            Playlist playlist = host.playlistController.createPlaylist("UI smoke");
            host.playlistController.addTrackToPlaylist(playlist, track);
            host.navigationState.tabIndex = LibraryTabs.HOME;
            host.librarySnapshotApplier.rebuildDerivedAndRender();
        });
        InstrumentedTestSupport.waitFor("Home collections did not render", 5000L,
                () -> findText(host.list, Button.class, "UI smoke") != null
                        && findText(host.list, Button.class, "UI album") != null);
        assertTrue(findText(host.list, Button.class, "UI smoke").getBackground()
                instanceof GradientDrawable);
        assertTrue(findText(host.list, Button.class, "UI album").getBackground()
                instanceof GradientDrawable);

        instrumentation.runOnMainSync(() -> {
            host.switchTabAnimated(LibraryTabs.SONGS, 1);
        });
        InstrumentedTestSupport.waitFor("Songs tab did not open", 5000L,
                () -> host.navigationState.tabIndex == LibraryTabs.SONGS);
        assertTrue(host.libraryState.favorites.contains(track.uri));
        assertFalse(host.libraryState.playlists.isEmpty());

        instrumentation.runOnMainSync(() ->
                host.switchTabAnimated(LibraryTabs.FAVORITES, 1));
        InstrumentedTestSupport.waitFor("Favorite track did not render", 5000L,
                () -> host.navigationState.tabIndex == LibraryTabs.FAVORITES
                        && findText(host.list, TextView.class, track.title) != null);

        instrumentation.runOnMainSync(() -> {
            host.switchTabAnimated(LibraryTabs.PLAYLISTS, 1);
            assertNotNull("Playlist preview was empty during the first transition frame",
                    findText(host.contentHost, TextView.class, "UI smoke"));
        });
        InstrumentedTestSupport.waitFor("Playlists tab did not open", 5000L,
                () -> host.navigationState.tabIndex == LibraryTabs.PLAYLISTS
                        && findText(host.list, TextView.class, "UI smoke") != null);
        InstrumentedTestSupport.waitFor("Compact playlist card was not laid out", 5000L,
                () -> host.list.findViewById(R.id.playlist_card) != null
                        && host.list.findViewById(R.id.playlist_card).getHeight() > 0);
        FrameLayoutCover playlistCover = find(host.list, FrameLayoutCover.class);
        assertNotNull(playlistCover);
        View playlistCard = host.list.findViewById(R.id.playlist_card);
        assertNotNull(playlistCard);
        assertEquals(host.getResources().getDimensionPixelSize(R.dimen.playlist_card_height),
                playlistCard.getHeight());
        assertEquals(host.getResources().getDimensionPixelSize(R.dimen.playlist_cover_size),
                playlistCover.getHeight());
        assertEquals("Playlist artwork must not keep an empty transition layer",
                1, playlistCover.getChildCount());
        assertNull("Playlist artwork container must not expose square fallback corners",
                playlistCover.getBackground());
        assertTrue(playlistCover.getChildAt(0).getAlpha() > 0.99f);

        instrumentation.runOnMainSync(() ->
                host.switchTabAnimated(LibraryTabs.SETTINGS, 1));
        InstrumentedTestSupport.waitFor("Settings tab did not open", 5000L,
                () -> host.navigationState.tabIndex == LibraryTabs.SETTINGS);
    }

    private void assertFullPlayerPages(MainActivityCore host, Track track) {
        instrumentation.runOnMainSync(() -> {
            host.overlayHost.removeAllViews();
            host.playbackUiState.queue.clear();
            host.playbackUiState.queue.add(track);
            String mediaId = MediaItemMapper.stableHash(track.uri);
            host.updatePlaybackSnapshot(new PlaybackSnapshot(
                    Collections.singletonList(mediaId), mediaId, 0, 0L, track.durationMs,
                    false, Player.STATE_READY, Player.REPEAT_MODE_OFF, false,
                    PlaybackPhase.READY, PauseReason.NONE, StopReason.NONE,
                    null, System.currentTimeMillis()));
            host.playerUiController.openFullPlayer();
        });
        InstrumentedTestSupport.waitFor("Full player did not open", 5000L,
                () -> find(host.overlayHost, ViewPager2.class) != null);
        ViewPager2 pager = find(host.overlayHost, ViewPager2.class);
        assertEquals(FullPlayerPageOrder.PLAYER, pager.getCurrentItem());
        View lyricsTile = findDescription(host.overlayHost, "Открыть текст");
        assertNotNull(lyricsTile);
        instrumentation.runOnMainSync(lyricsTile::performClick);
        InstrumentedTestSupport.waitFor("Missing lyrics state did not render", 5000L,
                () -> pager.getCurrentItem() == FullPlayerPageOrder.LYRICS
                        && containsText(host.overlayHost, "Текст не определён"));
        View queueTile = findDescription(host.overlayHost, "Открыть очередь");
        assertNotNull(queueTile);
        instrumentation.runOnMainSync(queueTile::performClick);
        InstrumentedTestSupport.waitFor("Queue tile did not open the queue", 5000L,
                () -> pager.getCurrentItem() == FullPlayerPageOrder.QUEUE);
        RecyclerView queueList = findQueueList(host.overlayHost);
        assertNotNull(queueList);
        InstrumentedTestSupport.waitFor("Queue row did not render", 5000L,
                () -> queueList.getChildCount() > 0);
        int queueSize = host.playbackUiState.queue.size();
        swipeRight(queueList, queueList.getChildAt(0));
        InstrumentedTestSupport.waitFor("Right swipe did not leave the queue", 5000L,
                () -> pager.getCurrentItem() == FullPlayerPageOrder.LYRICS);
        assertEquals("Right swipe must navigate without removing a queue item",
                queueSize, host.playbackUiState.queue.size());
        View playerTile = findDescription(host.overlayHost, "Открыть плеер");
        assertNotNull(playerTile);
        instrumentation.runOnMainSync(playerTile::performClick);
        InstrumentedTestSupport.waitFor("Player tile did not return to the player", 5000L,
                () -> pager.getCurrentItem() == FullPlayerPageOrder.PLAYER);
        instrumentation.runOnMainSync(host.overlayHost::removeAllViews);
    }

    private static View findDescription(View view, String expected) {
        CharSequence description = view.getContentDescription();
        if (description != null && expected.contentEquals(description)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findDescription(group.getChildAt(index), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static RecyclerView findQueueList(View view) {
        if (view instanceof RecyclerView
                && ((RecyclerView) view).getAdapter() instanceof QueueAdapter) {
            return (RecyclerView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                RecyclerView found = findQueueList(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void swipeRight(RecyclerView target, View row) {
        float startX = row.getLeft() + row.getWidth() * 0.25f;
        float endX = row.getLeft() + row.getWidth() * 0.85f;
        float y = row.getTop() + row.getHeight() * 0.5f;
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(target, MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, startX, y, 0));
        for (int step = 1; step <= 8; step++) {
            long eventTime = downTime + step * 18L;
            float x = startX + ((endX - startX) * step / 8.0f);
            dispatchTouch(target, MotionEvent.obtain(
                    downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0));
        }
        dispatchTouch(target, MotionEvent.obtain(
                downTime, downTime + 180L, MotionEvent.ACTION_UP, endX, y, 0));
    }

    private void dispatchTouch(View target, MotionEvent event) {
        instrumentation.runOnMainSync(() -> target.dispatchTouchEvent(event));
        event.recycle();
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (containsText(group.getChildAt(index), expected)) return true;
            }
        }
        return false;
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                T found = find(group.getChildAt(index), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends TextView> T findText(View view, Class<T> type,
            String expected) {
        if (type.isInstance(view) && expected.contentEquals(((TextView) view).getText())) {
            return type.cast(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                T found = findText(group.getChildAt(index), type, expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private MainActivityCore launchWithLibrary() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        context.getSharedPreferences("mp3_player_store", Context.MODE_PRIVATE).edit()
                .putBoolean("sqlite_migrated", true)
                .commit();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putString("language", "ru")
                .putBoolean("animations", true)
                .putBoolean("particlesEnabled", false)
                .commit();
        ArrayList<Track> tracks = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            tracks.add(new Track("content://voltune.ui/track/" + index,
                    "UI song " + index, "UI artist", "UI album", "UI genre", 180000));
        }
        TrackStore.save(context, tracks);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, tracks.size())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        activity = monitor.waitForActivityWithTimeout(15000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull(activity);
        MainActivityCore host = (MainActivityCore) activity;
        InstrumentedTestSupport.waitFor("Test library did not load", 10000L,
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
