package com.dumuzeyn.mp3player;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;

/** Selects and activates the launcher tile nearest to the current custom theme. */
final class LauncherComponents {
    private static final String CLASS_PACKAGE = MainActivity.class.getPackage().getName();

    private LauncherComponents() {
    }

    static ComponentName forTheme(Context context, boolean dark) {
        return component(context, dark ? "Dark" : "Light");
    }

    static ComponentName forThemeState(Context context, String theme,
            boolean dark, int customBackground, int primaryAccent, int secondaryAccent) {
        if (!"custom".equals(theme)) {
            return forTheme(context, dark);
        }
        BackgroundPalette closestBackground = backgroundPalettes(context)[0];
        double closestDistance = Double.MAX_VALUE;
        for (BackgroundPalette palette : backgroundPalettes(context)) {
            double distance = perceptualDistance(customBackground, palette.background);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestBackground = palette;
            }
        }
        AccentPalette closestAccent = accentPalettes(context)[0];
        closestDistance = Double.MAX_VALUE;
        for (AccentPalette palette : accentPalettes(context)) {
            double distance = accentDistance(primaryAccent, palette.primary)
                    + accentDistance(secondaryAccent, palette.secondary) * 0.55;
            if (distance < closestDistance) {
                closestDistance = distance;
                closestAccent = palette;
            }
        }
        return component(context, customSuffix(closestBackground, closestAccent));
    }

    static boolean apply(Context context, ComponentName selected) {
        PackageManager manager = context.getPackageManager();
        ComponentName[] components = all(context);
        int activeCount = 0;
        boolean selectedActive = false;
        for (ComponentName component : components) {
            if (isEnabled(manager, context, component)) {
                activeCount++;
                selectedActive |= selected.equals(component);
            }
        }
        if (selectedActive && activeCount == 1) {
            return false;
        }
        manager.setComponentEnabledSetting(selected,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        for (ComponentName component : components) {
            if (!component.equals(selected)) {
                manager.setComponentEnabledSetting(component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }
        return true;
    }

    static ComponentName[] all(Context context) {
        BackgroundPalette[] backgrounds = backgroundPalettes(context);
        AccentPalette[] accents = accentPalettes(context);
        ComponentName[] components = new ComponentName[2 + backgrounds.length * accents.length];
        components[0] = forTheme(context, false);
        components[1] = forTheme(context, true);
        int index = 2;
        for (BackgroundPalette background : backgrounds) {
            for (AccentPalette accent : accents) {
                components[index++] = component(context, customSuffix(background, accent));
            }
        }
        return components;
    }

    static double perceptualDistance(int first, int second) {
        double[] firstLab = toLab(first);
        double[] secondLab = toLab(second);
        double lightness = firstLab[0] - secondLab[0];
        double greenRed = firstLab[1] - secondLab[1];
        double blueYellow = firstLab[2] - secondLab[2];
        return Math.sqrt(lightness * lightness + greenRed * greenRed
                + blueYellow * blueYellow);
    }

    private static double accentDistance(int first, int second) {
        float[] firstHsv = new float[3];
        float[] secondHsv = new float[3];
        Color.colorToHSV(first, firstHsv);
        Color.colorToHSV(second, secondHsv);
        double hue = Math.abs(firstHsv[0] - secondHsv[0]);
        hue = Math.min(hue, 360.0 - hue) / 180.0;
        double saturation = firstHsv[1] - secondHsv[1];
        double value = firstHsv[2] - secondHsv[2];
        return hue * hue * 1_000_000.0
                + saturation * saturation * 100_000.0
                + value * value * 10_000.0;
    }

    private static BackgroundPalette[] backgroundPalettes(Context context) {
        return new BackgroundPalette[] {
                background(context, "Blue", "Light", R.color.launcher_icon_blue_light_bg),
                background(context, "Blue", "Dark", R.color.launcher_icon_blue_dark_bg),
                background(context, "Red", "Light", R.color.launcher_icon_red_light_bg),
                background(context, "Red", "Dark", R.color.launcher_icon_red_dark_bg),
                background(context, "Green", "Light", R.color.launcher_icon_green_light_bg),
                background(context, "Green", "Dark", R.color.launcher_icon_green_dark_bg),
                background(context, "Pink", "Light", R.color.launcher_icon_pink_light_bg),
                background(context, "Pink", "Dark", R.color.launcher_icon_pink_dark_bg),
                background(context, "Orange", "Light", R.color.launcher_icon_orange_light_bg),
                background(context, "Orange", "Dark", R.color.launcher_icon_orange_dark_bg)
        };
    }

    private static AccentPalette[] accentPalettes(Context context) {
        return new AccentPalette[] {
                accent(context, "Blue", R.color.launcher_foreground_blue_primary,
                        R.color.launcher_foreground_blue_secondary),
                accent(context, "Red", R.color.launcher_foreground_red_primary,
                        R.color.launcher_foreground_red_secondary),
                accent(context, "Green", R.color.launcher_foreground_green_primary,
                        R.color.launcher_foreground_green_secondary),
                accent(context, "Pink", R.color.launcher_foreground_pink_primary,
                        R.color.launcher_foreground_pink_secondary),
                accent(context, "Orange", R.color.launcher_foreground_orange_primary,
                        R.color.launcher_foreground_orange_secondary)
        };
    }

    private static BackgroundPalette background(Context context, String family, String mode,
            int colorResource) {
        return new BackgroundPalette(family, mode, context.getColor(colorResource));
    }

    private static AccentPalette accent(Context context, String family,
            int primaryResource, int secondaryResource) {
        return new AccentPalette(family, context.getColor(primaryResource),
                context.getColor(secondaryResource));
    }

    private static String customSuffix(BackgroundPalette background, AccentPalette accent) {
        String suffix = "Custom" + background.family + background.mode;
        return background.family.equals(accent.family)
                ? suffix : suffix + "Foreground" + accent.family;
    }

    private static boolean isEnabled(PackageManager manager, Context context,
            ComponentName component) {
        int state = manager.getComponentEnabledSetting(component);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return true;
        }
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                && component.equals(forTheme(context, false));
    }

    private static ComponentName component(Context context, String suffix) {
        return new ComponentName(context.getPackageName(),
                CLASS_PACKAGE + ".Launcher" + suffix);
    }

    private static double[] toLab(int color) {
        double red = linear(Color.red(color) / 255.0);
        double green = linear(Color.green(color) / 255.0);
        double blue = linear(Color.blue(color) / 255.0);
        double x = pivot((red * 0.4124564 + green * 0.3575761 + blue * 0.1804375)
                / 0.95047);
        double y = pivot(red * 0.2126729 + green * 0.7151522 + blue * 0.0721750);
        double z = pivot((red * 0.0193339 + green * 0.1191920 + blue * 0.9503041)
                / 1.08883);
        return new double[] {116.0 * y - 16.0, 500.0 * (x - y), 200.0 * (y - z)};
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static double pivot(double value) {
        return value > 0.008856 ? Math.cbrt(value) : 7.787 * value + 16.0 / 116.0;
    }

    private static final class BackgroundPalette {
        final String family;
        final String mode;
        final int background;

        BackgroundPalette(String family, String mode, int background) {
            this.family = family;
            this.mode = mode;
            this.background = background;
        }
    }

    private static final class AccentPalette {
        final String family;
        final int primary;
        final int secondary;

        AccentPalette(String family, int primary, int secondary) {
            this.family = family;
            this.primary = primary;
            this.secondary = secondary;
        }
    }
}
