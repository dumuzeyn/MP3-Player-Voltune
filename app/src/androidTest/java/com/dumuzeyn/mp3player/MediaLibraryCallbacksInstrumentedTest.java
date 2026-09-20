package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaLibraryCallbacksInstrumentedTest {
    private Context context;
    private LibraryDatabase database;
    private VoltuneMediaLibraryCallback callback;
    private AtomicInteger handledCommands;

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
        handledCommands = new AtomicInteger();
        callback = new VoltuneMediaLibraryCallback(database, new MediaItemMapper(),
                new VoltuneMediaLibraryCallback.CommandDelegate() {
                    @Override
                    public ListenableFuture<SessionResult> handle(
                            androidx.media3.session.MediaSession.ControllerInfo controller,
                            SessionCommand command, Bundle args) {
                        handledCommands.incrementAndGet();
                        return Futures.immediateFuture(
                                new SessionResult(SessionResult.RESULT_SUCCESS));
                    }

                    @Override
                    public void onCommand(String action) {
                    }
                }, new Media3ControllerAccess(Process.myUid(), context.getPackageName()),
                context.getPackageName() + ".artwork");
    }

    @After
    public void tearDown() {
        callback.close();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @Test
    public void externalLibraryOnlyPublishesSafeMetadataAndResolvesMediaIdInternally()
            throws Exception {
        LibraryResult<MediaItem> root = callback.browseRoot(null).get(2, TimeUnit.SECONDS);
        ImmutableList<MediaItem> categories = callback.browseChildren(
                root.value.mediaId, 0, 20, null).get(2, TimeUnit.SECONDS).value;
        String songsId = findCategory(categories, "Songs").mediaId;
        MediaItem publicItem = callback.browseChildren(songsId, 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.get(0);

        assertEquals("Alpha", publicItem.mediaMetadata.title.toString());
        assertEquals("Artist A", publicItem.mediaMetadata.artist.toString());
        assertEquals("Album A", publicItem.mediaMetadata.albumTitle.toString());
        assertNull(publicItem.localConfiguration);
        assertNull(publicItem.mediaMetadata.extras);
        assertNotNull(publicItem.mediaMetadata.artworkUri);
        assertEquals(context.getPackageName() + ".artwork",
                publicItem.mediaMetadata.artworkUri.getAuthority());
        assertFalse(publicItem.mediaMetadata.artworkUri.toString().contains("content://library"));

        androidx.media3.session.MediaSession.ControllerInfo external = controller(
                "com.example.external", Process.myUid() + 10000, false);
        java.util.List<MediaItem> resolved = callback.resolveMediaItems(
                external, Arrays.asList(publicItem)).get(2, TimeUnit.SECONDS);
        assertEquals(1, resolved.size());
        assertNotNull(resolved.get(0).localConfiguration);
        assertEquals("content://library/one",
                resolved.get(0).localConfiguration.uri.toString());
    }

    @Test
    public void externalControllerCannotInvokeClearQueueDirectly() throws Exception {
        androidx.media3.session.MediaSession.ControllerInfo external = controller(
                "com.example.external", Process.myUid() + 10000, false);
        SessionResult result = callback.handleCustomCommand(
                external, Media3Commands.CLEAR_QUEUE_COMMAND, Bundle.EMPTY)
                .get(2, TimeUnit.SECONDS);
        assertEquals(androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED,
                result.resultCode);
        assertEquals(0, handledCommands.get());
    }

    @Test
    public void ownControllerCanInvokeInternalCommand() throws Exception {
        androidx.media3.session.MediaSession.ControllerInfo own = controller(
                context.getPackageName(), Process.myUid(), true);
        SessionResult result = callback.handleCustomCommand(
                own, Media3Commands.CLEAR_QUEUE_COMMAND, Bundle.EMPTY)
                .get(2, TimeUnit.SECONDS);
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
        assertEquals(1, handledCommands.get());
    }

    private static androidx.media3.session.MediaSession.ControllerInfo controller(
            String packageName, int uid, boolean trusted) {
        return androidx.media3.session.MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                packageName, 1234, uid, 1, 1, trusted, Bundle.EMPTY, true);
    }

    @Test
    public void rootSongsPlaylistsSmartListsAndSearchAreBrowsable() throws Exception {
        LibraryResult<MediaItem> root = callback.browseRoot(null)
                .get(2, TimeUnit.SECONDS);
        assertNotNull(root.value);
        LibraryResult<ImmutableList<MediaItem>> categories = callback.browseChildren(
                root.value.mediaId, 0, 20, null).get(2, TimeUnit.SECONDS);
        assertEquals(5, categories.value.size());

        String songsId = findCategory(categories.value, "Songs").mediaId;
        String playlistsId = findCategory(categories.value, "Playlists").mediaId;
        String smartId = findCategory(categories.value, "Smart playlists").mediaId;
        assertEquals(2, callback.browseChildren(songsId, 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.size());
        assertEquals(1, callback.browseChildren(playlistsId, 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.size());
        assertFalse(callback.browseChildren(smartId, 0, 20, null)
                .get(2, TimeUnit.SECONDS).value.isEmpty());
        assertEquals("Alpha", callback.browseSearchResult(" alpha ", 0, 20, null)
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
