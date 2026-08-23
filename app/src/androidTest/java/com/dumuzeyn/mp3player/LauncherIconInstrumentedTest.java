package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LauncherIconInstrumentedTest {
    @Test
    public void launcherAliasesUseTheirThemeResources() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(context.getPackageName(),
                LauncherComponents.forTheme(context, true).getPackageName());
        PackageManager packageManager = context.getPackageManager();
        assertAliasIcon(packageManager, context, "LauncherLight", R.mipmap.ic_launcher_home);
        assertAliasIcon(packageManager, context, "LauncherDark", R.mipmap.ic_launcher_dark);
        assertAliasIcon(packageManager, context, "LauncherCustomBlueLight",
                R.mipmap.ic_launcher_custom_blue_light);
        assertAliasIcon(packageManager, context, "LauncherCustomBlueDark",
                R.mipmap.ic_launcher_custom_blue_dark);
        assertAliasIcon(packageManager, context, "LauncherCustomRedLight",
                R.mipmap.ic_launcher_custom_red_light);
        assertAliasIcon(packageManager, context, "LauncherCustomRedDark",
                R.mipmap.ic_launcher_custom_red_dark);
        assertAliasIcon(packageManager, context, "LauncherCustomGreenLight",
                R.mipmap.ic_launcher_custom_green_light);
        assertAliasIcon(packageManager, context, "LauncherCustomGreenDark",
                R.mipmap.ic_launcher_custom_green_dark);
        assertAliasIcon(packageManager, context, "LauncherCustomPinkLight",
                R.mipmap.ic_launcher_custom_pink_light);
        assertAliasIcon(packageManager, context, "LauncherCustomPinkDark",
                R.mipmap.ic_launcher_custom_pink_dark);
        assertAliasIcon(packageManager, context, "LauncherCustomOrangeLight",
                R.mipmap.ic_launcher_custom_orange_light);
        assertAliasIcon(packageManager, context, "LauncherCustomOrangeDark",
                R.mipmap.ic_launcher_custom_orange_dark);
    }

    @Test
    public void runtimeIconUsesRequestedBackgroundAndGradient() {
        Context context = ApplicationProvider.getApplicationContext();
        int background = Color.rgb(12, 34, 56);
        Bitmap tile = AppIconRenderer.renderTile(context, background,
                Color.RED, Color.GREEN, 192);
        assertEquals(background, tile.getPixel(tile.getWidth() / 2, 2));

        Bitmap logo = AppIconRenderer.renderLogo(context, Color.RED, Color.GREEN, 192);
        long leftRed = 0;
        long leftGreen = 0;
        long rightRed = 0;
        long rightGreen = 0;
        for (int y = 0; y < logo.getHeight(); y++) {
            for (int x = 0; x < logo.getWidth(); x++) {
                int color = logo.getPixel(x, y);
                if (Color.alpha(color) == 0) {
                    continue;
                }
                if (x < logo.getWidth() / 2) {
                    leftRed += Color.red(color);
                    leftGreen += Color.green(color);
                } else {
                    rightRed += Color.red(color);
                    rightGreen += Color.green(color);
                }
            }
        }
        assertTrue(leftRed > leftGreen);
        assertTrue(rightGreen > rightRed);
        tile.recycle();
        logo.recycle();
    }

    @Test
    public void customColorsSelectTheMatchingLauncherPalette() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("LauncherCustomBlueLight", simpleName(LauncherComponents.forPalette(
                context, "custom", false, 0xff3478f6, 0xff40d7ff)));
        assertEquals("LauncherCustomRedDark", simpleName(LauncherComponents.forPalette(
                context, "custom", true, 0xffff4d67, 0xffffb23e)));
        assertEquals("LauncherCustomGreenLight", simpleName(LauncherComponents.forPalette(
                context, "custom", false, 0xff25b86b, 0xffc8f04b)));
        assertEquals("LauncherCustomPinkDark", simpleName(LauncherComponents.forPalette(
                context, "custom", true, 0xffe94baa, 0xff36d8d0)));
        assertEquals("LauncherCustomOrangeLight", simpleName(LauncherComponents.forPalette(
                context, "custom", false, 0xffff3545, 0xffd0e8ff)));
        assertEquals("LauncherCustomBlueLight", simpleName(LauncherComponents.forPalette(
                context, "custom", false, 0xff09094f, 0xff87dcec)));
    }

    private static String simpleName(ComponentName component) {
        String className = component.getClassName();
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static void assertAliasIcon(PackageManager packageManager, Context context,
            String className, int expectedIcon) throws Exception {
        ComponentName component = new ComponentName(
                context.getPackageName(), MainActivity.class.getPackage().getName()
                        + "." + className);
        assertEquals(expectedIcon, packageManager.getActivityInfo(
                component, PackageManager.MATCH_DISABLED_COMPONENTS).icon);
    }
}
