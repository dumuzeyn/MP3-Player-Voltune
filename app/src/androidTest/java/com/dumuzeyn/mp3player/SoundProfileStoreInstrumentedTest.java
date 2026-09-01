package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SoundProfileStoreInstrumentedTest {
    private Context context;
    private LibraryDatabase library;
    private SoundProfileStore profiles;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        library = new LibraryDatabase(context);
        profiles = new SoundProfileStore(context);
    }

    @After
    public void tearDown() {
        profiles.close();
        library.close();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @Test
    public void profileAndAssignmentSurviveReload() {
        Track track = track("one");
        library.upsertTrack(track);
        TrackAudioProfile profile = TrackAudioProfile.analyzed(track, features(2.0d));
        profiles.saveProfile(profile);
        SoundGroup group = new SoundGroup("sound-one", "Яркий тембр", "Bright timbre",
                features(0.5d), Arrays.asList(track.trackId));
        profiles.replaceGroups(Arrays.asList(group));

        assertEquals(1, profiles.loadProfiles().size());
        assertEquals(1, profiles.loadGroups().size());
        assertEquals(track.trackId, profiles.loadGroups().get(0).trackIds.get(0));
    }

    @Test
    public void deletingTrackRemovesItsProfileAndEmptyGroup() {
        Track track = track("delete");
        library.upsertTrack(track);
        profiles.saveProfile(TrackAudioProfile.analyzed(track, features(1.0d)));
        profiles.replaceGroups(Arrays.asList(new SoundGroup("sound-delete", "Чёткий пульс",
                "Crisp pulse", features(0.2d), Arrays.asList(track.trackId))));

        library.deleteTrack(track.trackId);
        profiles.pruneEmptyGroups();

        assertTrue(profiles.loadProfiles().isEmpty());
        assertTrue(profiles.loadGroups().isEmpty());
    }

    @Test
    public void replacingLibraryAfterFolderRemovalLeavesNoProfiles() {
        Track first = track("folder-one");
        Track second = track("folder-two");
        library.saveTracks(Arrays.asList(first, second));
        profiles.saveProfile(TrackAudioProfile.analyzed(first, features(1.0d)));
        profiles.saveProfile(TrackAudioProfile.analyzed(second, features(2.0d)));

        library.saveTracks(new ArrayList<>());
        profiles.pruneEmptyGroups();

        assertTrue(profiles.loadProfiles().isEmpty());
    }

    @Test
    public void fullReanalysisResetClearsProfilesAndGroupsButKeepsLibrary() {
        Track track = track("reanalyze");
        library.upsertTrack(track);
        profiles.saveProfile(TrackAudioProfile.analyzed(track, features(1.0d)));
        profiles.replaceGroups(Arrays.asList(new SoundGroup("sound-reanalyze",
                "Глубокий бас", "Deep bass", features(0.2d),
                Arrays.asList(track.trackId))));

        profiles.clearAnalysis();

        assertTrue(profiles.loadProfiles().isEmpty());
        assertTrue(profiles.loadGroups().isEmpty());
        assertEquals(1, library.loadTracks().size());
    }

    private static Track track(String id) {
        return new Track(id, "content://sound/" + id, "Title", "Artist", "Album",
                "Genre", 120000, 100L, 200L, "fingerprint");
    }

    private static double[] features(double value) {
        double[] result = new double[TrackAudioProfile.FEATURE_COUNT];
        Arrays.fill(result, value);
        return result;
    }
}
