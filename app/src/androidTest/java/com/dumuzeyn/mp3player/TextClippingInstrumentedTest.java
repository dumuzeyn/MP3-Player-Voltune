package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.text.Layout;
import android.text.TextUtils;
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
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    @Test
    public void russianScreensAndDialogsDoNotClipText() {
        MainActivityCore host = launchRussianActivity();
        assertNoClipping("main screen", host.root);

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
        instrumentation.waitForIdleSync();
        assertNoClipping(name, host.overlayHost);
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
