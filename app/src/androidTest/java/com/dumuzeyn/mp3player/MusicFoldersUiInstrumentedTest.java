package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MusicFoldersUiInstrumentedTest {
    private Context context;
    private Instrumentation instrumentation;
    private MainActivityCore activity;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LibraryDatabase.DB_NAME);
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putString("language", "ru")
                .putBoolean("animations", false)
                .putBoolean("particlesEnabled", false)
                .commit();
        LibrarySourceStore store = new LibrarySourceStore(context);
        for (int index = 0; index < 9; index++) {
            store.remember(Uri.parse("content://provider/tree/folder-" + index),
                    "Очень длинное название музыкальной папки номер " + index, false);
        }
        store.close();
    }

    @After
    public void tearDown() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
        }
        context.deleteDatabase(LibraryDatabase.DB_NAME);
    }

    @Test
    public void folderListAndRemovalConfirmationStayInsideScreen() {
        activity = launch();
        instrumentation.runOnMainSync(activity.settingsController::openMusicFolders);
        InstrumentedTestSupport.waitFor("Music folders did not open", 5000L,
                () -> activity.overlayHost.getChildCount() > 0
                        && activity.overlayHost.getChildAt(0).getWidth() > 0);
        assertViewTreeInside(activity.overlayHost, activity.root);
        Button remove = findButton(activity.overlayHost,
                "Убрать папку из Voltune:");
        assertNotNull("Folder remove action is missing", remove);

        instrumentation.runOnMainSync(remove::performClick);
        InstrumentedTestSupport.waitFor("Removal confirmation did not open", 5000L,
                () -> containsText(activity.overlayHost,
                        "Файлы на устройстве останутся без изменений."));
        assertViewTreeInside(activity.overlayHost, activity.root);
    }

    @Test
    public void folderListFitsShortLandscapeWindow() {
        activity = launch();
        instrumentation.runOnMainSync(() -> activity.setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        InstrumentedTestSupport.waitFor("Landscape layout did not settle", 10000L,
                () -> activity.root.getWidth() > activity.root.getHeight());
        instrumentation.runOnMainSync(activity.settingsController::openMusicFolders);
        InstrumentedTestSupport.waitFor("Landscape folder list did not open", 5000L,
                () -> activity.overlayHost.getChildCount() > 0
                        && activity.overlayHost.getChildAt(0).getWidth() > 0);
        assertViewTreeInside(activity.overlayHost, activity.root);
    }

    private MainActivityCore launch() {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        MainActivity result = (MainActivity) monitor.waitForActivityWithTimeout(15000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("MainActivity did not start", result);
        InstrumentedTestSupport.waitFor("Main screen was not laid out", 10000L,
                () -> result.root != null && result.root.getWidth() > 0);
        return result;
    }

    private static void assertViewTreeInside(View view, View root) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() <= 0) {
            return;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int screenWidth = root.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = root.getResources().getDisplayMetrics().heightPixels;
        String bounds = " at " + location[0] + "," + location[1]
                + " size " + view.getWidth() + "x" + view.getHeight()
                + " on " + screenWidth + "x" + screenHeight;
        assertTrue("View extends left of the screen" + bounds, location[0] >= 0);
        assertTrue("View extends right of the screen" + bounds,
                location[0] + view.getWidth() <= screenWidth);
        assertTrue("View extends below the screen" + bounds,
                location[1] + view.getHeight() <= screenHeight);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (group instanceof ScrollView) {
                return;
            }
            for (int index = 0; index < group.getChildCount(); index++) {
                assertViewTreeInside(group.getChildAt(index), root);
            }
        }
    }

    private static Button findButton(View view, String descriptionPrefix) {
        if (view instanceof Button && view.getContentDescription() != null
                && view.getContentDescription().toString().startsWith(descriptionPrefix)) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                Button result = findButton(group.getChildAt(index), descriptionPrefix);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView
                && ((TextView) view).getText().toString().contains(expected)) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (containsText(group.getChildAt(index), expected)) {
                    return true;
                }
            }
        }
        return false;
    }
}
