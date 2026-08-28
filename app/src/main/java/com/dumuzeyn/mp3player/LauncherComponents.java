package com.dumuzeyn.mp3player;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;

/** Selects and activates the static launcher background nearest to the real theme background. */
final class LauncherComponents {
    private static final String CLASS_PACKAGE = MainActivity.class.getPackage().getName();

    private LauncherComponents() {
    }

    static ComponentName forTheme(Context context, boolean dark) {
        return component(context, dark ? "Dark" : "Light");
    }

    static ComponentName forThemeState(Context context, String theme,
            boolean dark, int customBackground) {
        if (!"custom".equals(theme)) {
            return forTheme(context, dark);
        }
        Palette closest = customPalettes(context)[0];
        double closestDistance = Double.MAX_VALUE;
        for (Palette palette : customPalettes(context)) {
            double distance = perceptualDistance(customBackground, palette.background);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = palette;
            }
        }
        return component(context, closest.componentSuffix);
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
        Palette[] palettes = customPalettes(context);
        ComponentName[] components = new ComponentName[2 + palettes.length];
        components[0] = forTheme(context, false);
        components[1] = forTheme(context, true);
        for (int index = 0; index < palettes.length; index++) {
            components[index + 2] = component(context, palettes[index].componentSuffix);
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

    private static Palette[] customPalettes(Context context) {
        return new Palette[] {
                palette(context, "CustomBlueLight", R.color.launcher_icon_blue_light_bg),
                palette(context, "CustomBlueDark", R.color.launcher_icon_blue_dark_bg),
                palette(context, "CustomRedLight", R.color.launcher_icon_red_light_bg),
                palette(context, "CustomRedDark", R.color.launcher_icon_red_dark_bg),
                palette(context, "CustomGreenLight", R.color.launcher_icon_green_light_bg),
                palette(context, "CustomGreenDark", R.color.launcher_icon_green_dark_bg),
                palette(context, "CustomPinkLight", R.color.launcher_icon_pink_light_bg),
                palette(context, "CustomPinkDark", R.color.launcher_icon_pink_dark_bg),
                palette(context, "CustomOrangeLight", R.color.launcher_icon_orange_light_bg),
                palette(context, "CustomOrangeDark", R.color.launcher_icon_orange_dark_bg)
        };
    }

    private static Palette palette(Context context, String suffix, int colorResource) {
        return new Palette(suffix, context.getColor(colorResource));
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

    private static final class Palette {
        final String componentSuffix;
        final int background;

        Palette(String componentSuffix, int background) {
            this.componentSuffix = componentSuffix;
            this.background = background;
        }
    }
}
