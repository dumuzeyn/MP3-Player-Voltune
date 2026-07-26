package com.dumuzeyn.mp3player;

/** WCAG contrast check which preserves a selected text color and requests an outline. */
public final class ThemeContrastPolicy {
    private static final double MINIMUM_BODY_TEXT_CONTRAST = 4.5;

    private ThemeContrastPolicy() {
    }

    public static boolean requiresOutline(int textColor, int backgroundColor) {
        return contrastRatio(textColor, backgroundColor) < MINIMUM_BODY_TEXT_CONTRAST;
    }

    public static boolean outlineIsDistinct(int textColor, int outlineColor) {
        return contrastRatio(textColor, outlineColor) >= 1.35;
    }

    public static double contrastRatio(int first, int second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        double red = channel((color >> 16) & 0xff);
        double green = channel((color >> 8) & 0xff);
        double blue = channel(color & 0xff);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928
                ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
