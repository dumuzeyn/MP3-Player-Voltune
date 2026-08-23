package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompatibilityInstrumentedTest {
    @Test
    public void applicationAndPlaybackServiceAreConfiguredForCurrentAndroid() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                context.getPackageName(), 0);
        assertNotNull(packageInfo.applicationInfo);
        assertEquals(Mp3PlayerApplication.class.getName(), packageInfo.applicationInfo.className);

        ServiceInfo serviceInfo = context.getPackageManager().getServiceInfo(
                new ComponentName(context, Media3PlayerService.class), 0);
        assertNotNull(serviceInfo);
        assertTrue(!serviceInfo.exported);
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue((serviceInfo.getForegroundServiceType()
                    & ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) != 0);
        }
        assertTrue(Build.VERSION.SDK_INT >= 26);
    }

    @Test
    public void tabletProfileUsesCenteredResponsiveLayout() {
        boolean tabletRequired = Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("requireTablet", "false"));
        if (!tabletRequired) {
            return;
        }

        Context context = ApplicationProvider.getApplicationContext();
        int smallestWidthDp = context.getResources().getConfiguration().smallestScreenWidthDp;
        assertTrue("Tablet CI profile must report at least 600 dp", smallestWidthDp >= 600);

        context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE).edit()
                .putBoolean("particlesEnabled", false)
                .putBoolean("animations", false)
                .commit();

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        Intent activityIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);
        Activity activity = monitor.waitForActivityWithTimeout(45000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("MainActivity must start on the tablet profile", activity);
        try {
            MainActivityCore host = (MainActivityCore) activity;
            InstrumentedTestSupport.waitFor("Tablet page must finish its first layout", 10000L,
                    () -> host.page != null && host.root != null
                            && host.page.getWidth() > 0 && host.root.getWidth() > 0);
            assertTrue(host.responsiveLayoutController.isTablet());
            assertTrue("Tablet page must be narrower than the full display",
                    host.page.getWidth() < host.root.getWidth());
            int maximumPageWidth = Math.round(
                    960 * context.getResources().getDisplayMetrics().density);
            assertTrue(host.page.getWidth() <= maximumPageWidth);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
