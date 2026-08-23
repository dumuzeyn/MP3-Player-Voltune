package com.dumuzeyn.mp3player.benchmark;

import android.os.Build;
import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import kotlin.Unit;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VoltuneBaselineProfile {
    private static final String PACKAGE_NAME = "com.dumuzeyn.mp3player.benchmark";

    @Rule public final BaselineProfileRule rule = new BaselineProfileRule();

    @Test public void criticalUserJourneys() {
        Assume.assumeTrue("Baseline profile generation requires Android 14+ on vendor devices",
                Build.VERSION.SDK_INT >= 34);
        rule.collect(PACKAGE_NAME, scope -> {
            scope.startActivityAndWait();
            click(scope, "Songs", "\u041f\u0435\u0441\u043d\u0438");
            click(scope, "Favorites", "\u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435");
            click(scope, "Settings", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438");
            click(scope, "Songs", "\u041f\u0435\u0441\u043d\u0438");
            return Unit.INSTANCE;
        });
    }

    private static void click(MacrobenchmarkScope scope, String english, String russian) {
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
