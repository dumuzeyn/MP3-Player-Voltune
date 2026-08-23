package com.dumuzeyn.mp3player.benchmark;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.FrameTimingMetric;
import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.Metric;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import java.util.Arrays;
import java.util.List;
import kotlin.Unit;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VoltuneMacrobenchmark {
    private static final String PACKAGE_NAME = "com.dumuzeyn.mp3player.benchmark";

    @Rule public final MacrobenchmarkRule rule = new MacrobenchmarkRule();

    @Test public void coldStartup() {
        startup(StartupMode.COLD);
    }

    @Test public void warmStartup() {
        startup(StartupMode.WARM);
    }

    @Test public void hotStartup() {
        Assume.assumeTrue("Hot startup tracing requires Android 14+ on vendor devices",
                Build.VERSION.SDK_INT >= 34);
        startup(StartupMode.HOT);
    }

    @Test public void largeLibraryScrollAndSectionSwitch() {
        rule.measureRepeated(PACKAGE_NAME,
                interactionMetrics(), compilationMode(), StartupMode.COLD, 5,
                scope -> {
                    scope.startActivityAndWait(benchmarkLibraryIntent());
                    scope.pressHome();
                    return Unit.INSTANCE;
                },
                scope -> {
                    scope.startActivityAndWait();
                    scope.getDevice().swipe(500, 1600, 500, 400, 20);
                    clickText(scope, "Favorites", "\u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435");
                    clickText(scope, "Songs", "\u041f\u0435\u0441\u043d\u0438");
                    return Unit.INSTANCE;
                });
    }

    private static Intent benchmarkLibraryIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(
                        PACKAGE_NAME,
                        "com.dumuzeyn.mp3player.LauncherLight"))
                .putExtra("voltuneBenchmarkTrackCount", 1000);
    }

    private void startup(StartupMode mode) {
        rule.measureRepeated(PACKAGE_NAME,
                Arrays.asList(new StartupTimingMetric()),
                compilationMode(), mode, 5,
                scope -> {
                    scope.pressHome();
                    return Unit.INSTANCE;
                },
                scope -> {
                    scope.startActivityAndWait();
                    return Unit.INSTANCE;
                });
    }

    private static CompilationMode compilationMode() {
        // Some Android 13 vendor builds reject the ART command used by Partial.
        return Build.VERSION.SDK_INT >= 34
                ? new CompilationMode.Partial()
                : new CompilationMode.Ignore();
    }

    private static List<Metric> interactionMetrics() {
        // FrameTimeline data is incomplete on some Android 13 vendor builds.
        return Build.VERSION.SDK_INT >= 34
                ? Arrays.asList(new FrameTimingMetric())
                : Arrays.asList(new StartupTimingMetric());
    }

    private static void clickText(MacrobenchmarkScope scope, String english, String russian) {
        UiObject2 target = scope.getDevice().findObject(By.textContains(english));
        if (target == null) {
            target = scope.getDevice().findObject(By.textContains(russian));
        }
        if (target != null) {
            target.click();
            scope.getDevice().waitForIdle();
        }
    }
}
