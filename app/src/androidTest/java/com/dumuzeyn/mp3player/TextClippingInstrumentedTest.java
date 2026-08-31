package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TextClippingInstrumentedTest {
    private static final float LARGE_TEXT_SCALE = 1.30f;

    private Instrumentation instrumentation;
    private Activity activity;

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentedTestSupport.finishActivity(instrumentation, activity);
        }
    }

    @Test
    public void russianScreensAndDialogsDoNotClipText() {
        MainActivityCore host = launchRussianActivity();
        assertNoClipping("main screen", host.root);
        checkAllLibrarySections(host);

        String longTitle = "Защитить фоновое воспроизведение";
        String longMessage = "Voltune запросит работу без ограничений батареи, затем откроет "
                + "свою системную страницу. Прокрутите её вниз и выключите приостановку.";
        checkDialog(host, "background permission prompt",
                () -> host.showActionPanel(longTitle, longMessage,
                        "Позже", "Настроить", true, () -> { }));
        checkDialog(host, "background playback settings",
                host.backgroundPlaybackSettingsController::openDialog);
        checkDialog(host, "language", host.settingsController::openLanguageDialog);
        checkDialog(host, "mini-player memory",
                host.settingsController::openResumeWindowDialog);
        checkDialog(host, "theme", host.themeController::openDialog);
        checkDialog(host, "background", host.backgroundSettingsController::openDialog);
        checkDialog(host, "card transparency",
                host.cardTransparencyController::openDialog);
        checkDialog(host, "cover rotation",
                host.coverRotationSettingsController::openDialog);
        checkDialog(host, "equalizer", host.equalizerController::openDialog);
        checkDialog(host, "particle settings",
                host.particleSettingsController::openDialog);
        checkDialog(host, "playlist ticker",
                host.playlistTickerSettingsController::openDialog);
        checkDialog(host, "sleep timer", host.sleepTimerController::openDialog);
        checkDialog(host, "volume leveling",
                host.volumeLevelingController::openDialog);
        checkDialog(host, "text input", () -> host.overlayController.showInput(
                "Название нового плейлиста", "Название плейлиста", "", false,
                value -> { }));
    }

    @Test
    public void homeTabStartsCenteredWithoutDuplicateContentTitle() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .clear().commit();
        MainActivityCore host = launchRussianActivity();
        assertEquals("Voltune", host.getString(R.string.app_name));
        assertEquals("Похожие", host.tabs[LibraryTabs.SOUND]);
        assertHomeTabCentered(host, "clean launch");
        List<String> duplicates = new ArrayList<>();
        instrumentation.runOnMainSync(() -> collectExactText(
                host.list, host.tabs[LibraryTabs.HOME], duplicates));
        assertTrue("Active tab title is duplicated in content: " + duplicates,
                duplicates.isEmpty());

        InstrumentedTestSupport.finishActivity(instrumentation, activity);
        activity = null;
        MainActivityCore relaunched = launchRussianActivity();
        assertHomeTabCentered(relaunched, "second launch");
    }

    @Test
    public void homeContentIsReusedAfterSectionSwitch() {
        MainActivityCore host = launchRussianActivity();
        View firstHomeContent = host.list.getChildAt(0);
        instrumentation.runOnMainSync(() -> {
            host.navigationState.tabIndex = LibraryTabs.FAVORITES;
            host.render();
            host.navigationState.tabIndex = LibraryTabs.HOME;
            host.render();
        });
        assertSame("Home content was rebuilt after returning from another section",
                firstHomeContent, host.list.getChildAt(0));
    }

    private MainActivityCore launchRussianActivity() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putString("language", "ru")
                .putBoolean("particlesEnabled", false)
                .putBoolean("animations", false)
                .commit();
        context.getSharedPreferences("background_playback_setup", Context.MODE_PRIVATE).edit()
                .putBoolean("prompted_v1", true)
                .commit();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity = monitor.waitForActivityWithTimeout(15000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("MainActivity did not start", activity);
        MainActivityCore host = (MainActivityCore) activity;
        InstrumentedTestSupport.waitFor("Main screen was not laid out", 10000L,
                () -> host.root != null && host.root.getWidth() > 0);
        return host;
    }

    private void checkDialog(MainActivityCore host, String name, Runnable openDialog) {
        Log.i("VoltuneClippingTest", "Checking " + name);
        instrumentation.runOnMainSync(() -> {
            host.overlayHost.removeAllViews();
            openDialog.run();
        });
        InstrumentedTestSupport.waitFor(name + " did not open", 5000L,
                () -> host.overlayHost.getChildCount() > 0
                        && host.overlayHost.getChildAt(0).getWidth() > 0);
        instrumentation.runOnMainSync(() -> {
            scaleText(host.overlayHost, LARGE_TEXT_SCALE);
            host.overlayHost.requestLayout();
        });
        InstrumentedTestSupport.waitFor(name + " did not finish layout", 5000L,
                () -> !host.overlayHost.isLayoutRequested());
        assertNoClipping(name, host.overlayHost);
        Log.i("VoltuneClippingTest", "Finished " + name);
    }

    private void checkAllLibrarySections(MainActivityCore host) {
        for (int index = 0; index < host.tabs.length; index++) {
            int tabIndex = index;
            instrumentation.runOnMainSync(() -> {
                host.navigationState.tabIndex = tabIndex;
                host.render();
            });
            InstrumentedTestSupport.waitFor("Tab did not finish layout", 5000L,
                    () -> !host.root.isLayoutRequested());
            assertNoClipping("tab " + host.tabs[index], host.root);
        }
    }

    private static int activeTabCenterOffset(MainActivityCore host) {
        if (host.tabsScroll == null || host.tabRow == null || host.tabsScroll.getWidth() == 0) {
            return Integer.MAX_VALUE;
        }
        int viewportCenter = host.tabsScroll.getScrollX() + host.tabsScroll.getWidth() / 2;
        int closest = Integer.MAX_VALUE;
        for (int index = 0; index < host.tabRow.getChildCount(); index++) {
            View child = host.tabRow.getChildAt(index);
            if (Integer.valueOf(LibraryTabs.HOME).equals(child.getTag())) {
                closest = Math.min(closest,
                        Math.abs(child.getLeft() + child.getWidth() / 2 - viewportCenter));
            }
        }
        return closest;
    }

    private static void assertHomeTabCentered(MainActivityCore host, String launchName) {
        InstrumentedTestSupport.waitFor("Home tab was not centered on " + launchName,
                5000L, () -> activeTabCenterOffset(host) <= 2);
    }

    private static void collectExactText(View view, String expected, List<String> found) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) {
            found.add(expected);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectExactText(group.getChildAt(index), expected, found);
            }
        }
    }

    private void assertNoClipping(String screen, View root) {
        List<String> violations = new ArrayList<>();
        instrumentation.runOnMainSync(() -> inspect(root, violations));
        assertTrue("Text clipping in " + screen + ": " + violations, violations.isEmpty());
    }

    private static void scaleText(View view, float scale) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.getTextSize() * scale);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                scaleText(group.getChildAt(index), scale);
            }
        }
    }

    private static void inspect(View view, List<String> violations) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() <= 0
                || view.getHeight() <= 0) {
            return;
        }
        if (view instanceof TextView && !(view instanceof EditText)) {
            inspectText((TextView) view, violations);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                inspect(group.getChildAt(index), violations);
            }
        }
    }

    private static void inspectText(TextView text, List<String> violations) {
        if (TextUtils.isEmpty(text.getText())) {
            return;
        }
        Layout layout = text.getLayout();
        if (layout == null || layout.getLineCount() == 0) {
            violations.add(label(text) + " has no text layout");
            return;
        }
        int availableHeight = text.getHeight()
                - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        if (layout.getHeight() > availableHeight + 2) {
            violations.add(label(text) + " is vertically clipped (needs "
                    + layout.getHeight() + "px, has " + availableHeight + "px)");
        }
        int availableWidth = text.getWidth()
                - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        if (text.getEllipsize() == null) {
            for (int line = 0; line < layout.getLineCount(); line++) {
                if (layout.getLineMax(line) > availableWidth + 2) {
                    violations.add(label(text) + " is horizontally clipped");
                    break;
                }
            }
            int lastLine = layout.getLineCount() - 1;
            if (layout.getLineEnd(lastLine) < text.length()) {
                violations.add(label(text) + " loses trailing text");
            }
        }
    }

    private static String label(TextView text) {
        String value = text.getText().toString().replace('\n', ' ');
        return '"' + value.substring(0, Math.min(42, value.length())) + '"';
    }
}
