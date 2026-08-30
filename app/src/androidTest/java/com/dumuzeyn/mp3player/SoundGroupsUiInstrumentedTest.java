package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SoundGroupsUiInstrumentedTest {
    private Instrumentation instrumentation;
    private Activity activity;

    @After
    public void tearDown() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    @Test
    public void savedGroupAppearsImmediatelyAndOpensTrackList() {
        MainActivityCore host = launchWithSavedGroup();
        instrumentation.runOnMainSync(() -> {
            host.navigationState.tabIndex = LibraryTabs.SOUND;
            host.render();
        });
        InstrumentedTestSupport.waitFor("Saved sound group did not render", 5000L,
                () -> findText(host.list, "Яркий тембр") != null);
        View group = findText(host.list, "Яркий тембр");
        assertNotNull(group);
        View clickable = clickableAncestor(group);
        assertNotNull(clickable);
        instrumentation.runOnMainSync(clickable::performClick);
        InstrumentedTestSupport.waitFor("Sound group did not open", 5000L,
                () -> host.overlayHost.getChildCount() > 0
                        && findText(host.overlayHost, "Sound UI 0") != null);
    }

    private MainActivityCore launchWithSavedGroup() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        context.getSharedPreferences("mp3_player_store", Context.MODE_PRIVATE).edit()
                .putBoolean("sqlite_migrated", true).commit();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putString("language", "ru").putBoolean("animations", false)
                .putBoolean("particlesEnabled", false)
                .putBoolean("soundAnalysisEnabled", true).commit();
        ArrayList<Track> tracks = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            tracks.add(new Track("sound-ui-" + index, "content://sound/ui/" + index,
                    "Sound UI " + index, "Artist", "Album", "Genre", 120000,
                    10L + index, 20L + index, "fingerprint-" + index));
        }
        TrackStore.save(context, tracks);
        SoundProfileStore store = new SoundProfileStore(context);
        ArrayList<String> ids = new ArrayList<>();
        for (int index = 0; index < tracks.size(); index++) {
            double[] features = new double[TrackAudioProfile.FEATURE_COUNT];
            Arrays.fill(features, index + 1.0d);
            store.saveProfile(TrackAudioProfile.analyzed(tracks.get(index), features));
            ids.add(tracks.get(index).trackId);
        }
        store.replaceGroups(Arrays.asList(new SoundGroup("sound-ui", "Яркий тембр",
                "Bright timbre", new double[TrackAudioProfile.FEATURE_COUNT], ids)));
        store.close();

        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity = monitor.waitForActivityWithTimeout(15000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull(activity);
        MainActivityCore host = (MainActivityCore) activity;
        InstrumentedTestSupport.waitFor("Sound library did not load", 10000L,
                () -> host.libraryState.tracks.size() == 4
                        && !host.soundAnalysisController.groups().isEmpty());
        return host;
    }

    private static TextView findText(View view, String expected) {
        if (view instanceof TextView
                && expected.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findText(group.getChildAt(index), expected);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View clickableAncestor(View source) {
        View current = source;
        while (current != null) {
            if (current.hasOnClickListeners()) {
                return current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }
}
