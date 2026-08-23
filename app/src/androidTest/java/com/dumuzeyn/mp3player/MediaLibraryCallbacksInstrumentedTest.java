package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaLibraryCallbacksInstrumentedTest {
    private Context context;
    private LibraryDatabase database;
    private VoltuneMediaLibraryCallback callback;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        database = new LibraryDatabase(context);
        database.saveTracks(Arrays.asList(
                new Track("content://library/one", "Alpha", "Artist A", "Album A", "Rock"),
                new Track("content://library/two", "Beta", "Artist B", "Album B", "Jazz")));
        Playlist playlist = new Playlist("Road");
        playlist.uris.add("content://library/one");
        database.savePlaylists(Arrays.asList(playlist));
        callback = new VoltuneMediaLibraryCallback(database, new MediaItemMapper(),
                new VoltuneMediaLibraryCallback.CommandDelegate() {
                    @Override
                    public ListenableFuture<SessionResult> handle(
                            SessionCommand command, Bundle args) {
                        return Futures.immediateFuture(
                                new SessionResult(SessionResult.RESULT_SUCCESS));
                    }

                    @Override
                    public void onCommand(String action) {
                    }
                });
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @Test
    public void rootSongsPlaylistsSmartListsAndSearchAreBrowsable() throws Exception {
        LibraryResult<MediaItem> root = callback.onGetLibraryRoot(null, null, null)
                .get(2, TimeUnit.SECONDS);
        assertNotNull(root.value);
        LibraryResult<ImmutableList<MediaItem>> categories = callback.onGetChildren(
                null, null, root.value.mediaId, 0, 20, null).get(2, TimeUnit.SECONDS);
        assertEquals(5, categories.value.size());

        String songsId = findCategory(categories.value, "Songs").mediaId;
        String playlistsId = findCategory(categories.value, "Playlists").mediaId;
        String smartId = findCategory(categories.value, "Smart playlists").mediaId;
        assertEquals(2, callback.onGetChildren(null, null, songsId, 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.size());
        assertEquals(1, callback.onGetChildren(null, null, playlistsId, 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.size());
        assertFalse(callback.onGetChildren(null, null, smartId, 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.isEmpty());
        assertEquals("Alpha", callback.onGetSearchResult(null, null, " alpha ", 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.get(0).mediaMetadata.title.toString());
    }

    private static MediaItem findCategory(ImmutableList<MediaItem> items, String title) {
        for (MediaItem item : items) {
            if (title.contentEquals(item.mediaMetadata.title)) {
                return item;
            }
        }
        throw new AssertionError("Missing category: " + title);
    }
}
