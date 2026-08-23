package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
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
public class AccessibilityInstrumentedTest {
    private Instrumentation instrumentation;
    private Activity activity;

    @After
    public void tearDown() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    @Test
    public void primaryInteractiveControlsHaveAccessibleLabels() {
        assertScreenHasAccessibleControls("en");
    }

    @Test
    public void russianScreenHasAccessibleControls() {
        assertScreenHasAccessibleControls("ru");
    }

    private void assertScreenHasAccessibleControls(String language) {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putString("language", language)
                .putBoolean("particlesEnabled", false)
                .putBoolean("animations", false)
                .commit();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity = monitor.waitForActivityWithTimeout(15000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("MainActivity did not start", activity);
        InstrumentedTestSupport.waitFor("Main screen was not laid out", 10000L,
                () -> ((MainActivityCore) activity).root != null
                        && ((MainActivityCore) activity).root.getWidth() > 0);

        List<String> violations = new ArrayList<>();
        instrumentation.runOnMainSync(() -> inspect(activity.getWindow().getDecorView(),
                violations));
        assertTrue("Unlabelled controls: " + violations, violations.isEmpty());
    }

    private static void inspect(View view, List<String> violations) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view.isClickable() || view.isLongClickable()) {
            CharSequence text = view instanceof TextView ? ((TextView) view).getText() : null;
            CharSequence description = view.getContentDescription();
            if (TextUtils.isEmpty(text) && TextUtils.isEmpty(description)) {
                violations.add(view.getClass().getSimpleName() + "#" + view.getId());
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                inspect(group.getChildAt(index), violations);
            }
        }
    }
}
