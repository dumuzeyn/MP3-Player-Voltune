package com.dumuzeyn.mp3player;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;

/** Selects the closest prebuilt launcher palette for Android's static icon resources. */
final class LauncherComponents {
    private static final String PACKAGE_NAME = MainActivity.class.getPackage().getName();
    private static final Palette[] CUSTOM_PALETTES = {
            new Palette("", 0xff8a2cc8, 0xffffd000),
            new Palette("CustomBlue", 0xff3478f6, 0xff40d7ff),
            new Palette("CustomRed", 0xffff4d67, 0xffffb23e),
            new Palette("CustomGreen", 0xff25b86b, 0xffc8f04b),
            new Palette("CustomPink", 0xffe94baa, 0xff36d8d0),
            new Palette("CustomOrange", 0xffff3545, 0xffd0e8ff)
    };

    private LauncherComponents() {
    }

    static ComponentName forTheme(Context context, boolean dark) {
        return component(dark ? "Dark" : "Light");
    }

    static ComponentName forPalette(Context context, String theme,
            boolean dark, int primaryColor, int secondaryColor) {
        if (!"custom".equals(theme)) {
            return forTheme(context, dark);
        }
        Palette closest = CUSTOM_PALETTES[0];
        long closestDistance = Long.MAX_VALUE;
        for (Palette palette : CUSTOM_PALETTES) {
            long distance = colorDistance(primaryColor, palette.primary)
                    + colorDistance(secondaryColor, palette.secondary);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = palette;
            }
        }
        if (closest.suffix.length() == 0) {
            return forTheme(context, dark);
        }
        return component(closest.suffix + (dark ? "Dark" : "Light"));
    }

    static ComponentName[] all(Context context) {
        ComponentName[] components = new ComponentName[2 + (CUSTOM_PALETTES.length - 1) * 2];
        int index = 0;
        components[index++] = forTheme(context, false);
        components[index++] = forTheme(context, true);
        for (int paletteIndex = 1; paletteIndex < CUSTOM_PALETTES.length; paletteIndex++) {
            String suffix = CUSTOM_PALETTES[paletteIndex].suffix;
            components[index++] = component(suffix + "Light");
            components[index++] = component(suffix + "Dark");
        }
        return components;
    }

    private static ComponentName component(String suffix) {
        return new ComponentName(PACKAGE_NAME, PACKAGE_NAME + ".Launcher" + suffix);
    }

    private static long colorDistance(int first, int second) {
        long red = Color.red(first) - Color.red(second);
        long green = Color.green(first) - Color.green(second);
        long blue = Color.blue(first) - Color.blue(second);
        return red * red + green * green + blue * blue;
    }

    private static final class Palette {
        final String suffix;
        final int primary;
        final int secondary;

        Palette(String suffix, int primary, int secondary) {
            this.suffix = suffix;
            this.primary = primary;
            this.secondary = secondary;
        }
    }
}
