package com.dumuzeyn.mp3player

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Selects and activates the launcher tile nearest to the current custom theme. */
internal object LauncherComponents {
    private val classPackage = MainActivity::class.java.`package`?.name.orEmpty()

    @JvmStatic
    fun forTheme(context: Context, dark: Boolean): ComponentName =
        component(context, if (dark) "Dark" else "Light")

    @JvmStatic
    fun forThemeState(
        context: Context,
        theme: String,
        dark: Boolean,
        customBackground: Int,
        primaryAccent: Int,
        secondaryAccent: Int,
    ): ComponentName {
        if (theme != "custom") return forTheme(context, dark)
        var closestBackground = backgroundPalettes(context)[0]
        var closestDistance = Double.MAX_VALUE
        for (palette in backgroundPalettes(context)) {
            val distance = perceptualDistance(customBackground, palette.background)
            if (distance < closestDistance) {
                closestDistance = distance
                closestBackground = palette
            }
        }
        var closestAccent = accentPalettes(context)[0]
        closestDistance = Double.MAX_VALUE
        for (palette in accentPalettes(context)) {
            val distance = accentDistance(primaryAccent, palette.primary) +
                accentDistance(secondaryAccent, palette.secondary) * 0.55
            if (distance < closestDistance) {
                closestDistance = distance
                closestAccent = palette
            }
        }
        return component(context, customSuffix(closestBackground, closestAccent))
    }

    @JvmStatic
    fun apply(context: Context, selected: ComponentName): Boolean {
        val manager = context.packageManager
        val components = all(context)
        var activeCount = 0
        var selectedActive = false
        for (component in components) {
            if (isEnabled(manager, context, component)) {
                activeCount++
                selectedActive = selectedActive || selected == component
            }
        }
        if (selectedActive && activeCount == 1) return false
        manager.setComponentEnabledSetting(
            selected,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        for (component in components) {
            if (component != selected) {
                manager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
        return true
    }

    @JvmStatic
    fun all(context: Context): Array<ComponentName> {
        val backgrounds = backgroundPalettes(context)
        val accents = accentPalettes(context)
        val components = arrayOfNulls<ComponentName>(2 + backgrounds.size * accents.size)
        components[0] = forTheme(context, false)
        components[1] = forTheme(context, true)
        var index = 2
        for (background in backgrounds) {
            for (accent in accents) {
                components[index++] = component(context, customSuffix(background, accent))
            }
        }
        @Suppress("UNCHECKED_CAST")
        return components as Array<ComponentName>
    }

    @JvmStatic
    fun perceptualDistance(first: Int, second: Int): Double {
        val firstLab = toLab(first)
        val secondLab = toLab(second)
        val lightness = firstLab[0] - secondLab[0]
        val greenRed = firstLab[1] - secondLab[1]
        val blueYellow = firstLab[2] - secondLab[2]
        return sqrt(lightness * lightness + greenRed * greenRed + blueYellow * blueYellow)
    }

    private fun accentDistance(first: Int, second: Int): Double {
        val firstHsv = FloatArray(3)
        val secondHsv = FloatArray(3)
        Color.colorToHSV(first, firstHsv)
        Color.colorToHSV(second, secondHsv)
        var hue = abs(firstHsv[0] - secondHsv[0]).toDouble()
        hue = min(hue, 360.0 - hue) / 180.0
        val saturation = (firstHsv[1] - secondHsv[1]).toDouble()
        val value = (firstHsv[2] - secondHsv[2]).toDouble()
        return hue * hue * 1_000_000.0 +
            saturation * saturation * 100_000.0 +
            value * value * 10_000.0
    }

    private fun backgroundPalettes(context: Context): Array<BackgroundPalette> = arrayOf(
        background(context, "Blue", "Light", R.color.launcher_icon_blue_light_bg),
        background(context, "Blue", "Dark", R.color.launcher_icon_blue_dark_bg),
        background(context, "Red", "Light", R.color.launcher_icon_red_light_bg),
        background(context, "Red", "Dark", R.color.launcher_icon_red_dark_bg),
        background(context, "Green", "Light", R.color.launcher_icon_green_light_bg),
        background(context, "Green", "Dark", R.color.launcher_icon_green_dark_bg),
        background(context, "Pink", "Light", R.color.launcher_icon_pink_light_bg),
        background(context, "Pink", "Dark", R.color.launcher_icon_pink_dark_bg),
        background(context, "Orange", "Light", R.color.launcher_icon_orange_light_bg),
        background(context, "Orange", "Dark", R.color.launcher_icon_orange_dark_bg),
    )

    private fun accentPalettes(context: Context): Array<AccentPalette> = arrayOf(
        accent(
            context,
            "Blue",
            R.color.launcher_foreground_blue_primary,
            R.color.launcher_foreground_blue_secondary,
        ),
        accent(
            context,
            "Red",
            R.color.launcher_foreground_red_primary,
            R.color.launcher_foreground_red_secondary,
        ),
        accent(
            context,
            "Green",
            R.color.launcher_foreground_green_primary,
            R.color.launcher_foreground_green_secondary,
        ),
        accent(
            context,
            "Pink",
            R.color.launcher_foreground_pink_primary,
            R.color.launcher_foreground_pink_secondary,
        ),
        accent(
            context,
            "Orange",
            R.color.launcher_foreground_orange_primary,
            R.color.launcher_foreground_orange_secondary,
        ),
    )

    private fun background(
        context: Context,
        family: String,
        mode: String,
        colorResource: Int,
    ): BackgroundPalette = BackgroundPalette(family, mode, context.getColor(colorResource))

    private fun accent(
        context: Context,
        family: String,
        primaryResource: Int,
        secondaryResource: Int,
    ): AccentPalette = AccentPalette(
        family,
        context.getColor(primaryResource),
        context.getColor(secondaryResource),
    )

    private fun customSuffix(background: BackgroundPalette, accent: AccentPalette): String {
        val suffix = "Custom${background.family}${background.mode}"
        return if (background.family == accent.family) suffix else suffix + "Foreground" + accent.family
    }

    private fun isEnabled(
        manager: PackageManager,
        context: Context,
        component: ComponentName,
    ): Boolean {
        val state = manager.getComponentEnabledSetting(component)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
            component == forTheme(context, false)
    }

    private fun component(context: Context, suffix: String): ComponentName = ComponentName(
        context.packageName,
        "$classPackage.Launcher$suffix",
    )

    private fun toLab(color: Int): DoubleArray {
        val red = linear(Color.red(color) / 255.0)
        val green = linear(Color.green(color) / 255.0)
        val blue = linear(Color.blue(color) / 255.0)
        val x = pivot(
            (red * 0.4124564 + green * 0.3575761 + blue * 0.1804375) / 0.95047,
        )
        val y = pivot(red * 0.2126729 + green * 0.7151522 + blue * 0.0721750)
        val z = pivot(
            (red * 0.0193339 + green * 0.1191920 + blue * 0.9503041) / 1.08883,
        )
        return doubleArrayOf(116.0 * y - 16.0, 500.0 * (x - y), 200.0 * (y - z))
    }

    private fun linear(channel: Double): Double = if (channel <= 0.04045) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }

    private fun pivot(value: Double): Double =
        if (value > 0.008856) cbrt(value) else 7.787 * value + 16.0 / 116.0

    private data class BackgroundPalette(
        val family: String,
        val mode: String,
        val background: Int,
    )

    private data class AccentPalette(
        val family: String,
        val primary: Int,
        val secondary: Int,
    )
}
