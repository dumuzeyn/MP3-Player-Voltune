package com.dumuzeyn.mp3player;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

final class ThemeController {
    private static final int COLOR_BACKGROUND = 0;
    private static final int COLOR_ACCENT = 1;
    private static final int COLOR_SECONDARY_ACCENT = 2;
    private static final int COLOR_TEXT = 3;
    private static final int COLOR_OUTLINE = 4;
    private static final String THEME = "theme";
    private static final String CUSTOM_BG = "customBg";
    private static final String CUSTOM_FG = "customFg";
    private static final String CUSTOM_SECONDARY_ACCENT = "customSecondaryAccent";

    private final MainActivityCore host;
    private boolean launcherUpdatePending;

    ThemeController(MainActivityCore host) {
        this.host = host;
    }

    void load(SharedPreferences prefs) {
        host.themeMode = prefs.getString(THEME, "light");
        if (!"light".equals(host.themeMode)
                && !"dark".equals(host.themeMode)
                && !"custom".equals(host.themeMode)) {
            host.themeMode = "light";
        }
        host.customBg = prefs.getInt(CUSTOM_BG, Color.WHITE);
        host.customFg = prefs.getInt(CUSTOM_FG, Color.BLACK);
        host.customSecondaryAccent = prefs.getInt(
                CUSTOM_SECONDARY_ACCENT, Color.rgb(255, 208, 0));
    }

    String themeName() {
        if ("dark".equals(host.themeMode)) {
            return host.tr("Dark", "Темная");
        }
        if ("custom".equals(host.themeMode)) {
            return host.tr("Custom", "Своя");
        }
        return host.tr("Light", "Светлая");
    }

    void applyPalette() {
        host.dark = isDarkTheme(host.themeMode, host.customBg);
        if ("custom".equals(host.themeMode)) {
            host.bg = host.customBg;
            host.fg = host.customFg;
            host.primaryText = host.customFg;
            host.secondaryText = mixColor(host.customFg, host.customBg, 0.58f);
            host.card = mixColor(host.customBg, host.customFg, host.dark ? 0.92f : 0.96f);
            host.cardStroke = mixColor(host.customFg, host.customBg, 0.18f);
            host.purple = host.customFg;
            host.purpleDark = mixColor(host.customFg, host.customBg, 0.82f);
            host.purpleSoft = mixColor(host.customFg, host.customBg, 0.18f);
            host.yellow = host.customSecondaryAccent;
            host.yellowDark = mixColor(host.customSecondaryAccent, host.customBg, 0.82f);
            host.yellowSoft = mixColor(host.customSecondaryAccent, host.customBg, 0.18f);
        } else if (host.dark) {
            host.bg = Color.rgb(17, 16, 21);
            host.fg = Color.WHITE;
            host.primaryText = Color.WHITE;
            host.secondaryText = Color.rgb(170, 164, 178);
            host.card = Color.rgb(26, 24, 31);
            host.cardStroke = Color.rgb(51, 46, 58);
            host.purple = Color.rgb(163, 92, 255);
            host.purpleDark = Color.rgb(124, 50, 232);
            host.purpleSoft = Color.rgb(44, 35, 58);
            host.yellow = Color.rgb(255, 212, 56);
            host.yellowDark = Color.rgb(231, 185, 0);
            host.yellowSoft = Color.rgb(72, 62, 33);
        } else {
            host.bg = Color.WHITE;
            host.fg = Color.rgb(24, 21, 29);
            host.primaryText = Color.rgb(24, 21, 29);
            host.secondaryText = Color.rgb(118, 113, 125);
            host.card = Color.rgb(250, 249, 252);
            host.cardStroke = Color.rgb(233, 229, 238);
            host.purple = Color.rgb(124, 50, 232);
            host.purpleDark = Color.rgb(97, 32, 197);
            host.purpleSoft = Color.rgb(238, 228, 255);
            host.yellow = Color.rgb(255, 208, 0);
            host.yellowDark = Color.rgb(231, 185, 0);
            host.yellowSoft = Color.rgb(255, 245, 190);
        }
        if ("custom".equals(host.themeMode) && host.customTextColor != 0) {
            host.fg = host.customTextColor;
            host.primaryText = host.customTextColor;
            host.secondaryText = mixColor(host.customTextColor, host.bg, 0.58f);
        }
        host.muted = host.secondaryText;
        host.line = host.cardStroke;
        host.panel = host.card;
    }

    void applyWindow() {
        host.getWindow().setBackgroundDrawable(new ColorDrawable(host.bg));
        host.getWindow().setStatusBarColor(host.bg);
        host.getWindow().setNavigationBarColor(host.bg);
        if (Build.VERSION.SDK_INT >= 23) {
            host.getWindow().getDecorView().setSystemUiVisibility(host.dark ? 0 : 8192);
        }
        updateTaskPreview();
    }

    void openDialog() {
        final FrameLayout shade = host.shade();
        LinearLayout panel = host.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.text(host.tr("Theme", "Тема"), 22, true),
                new LinearLayout.LayoutParams(-1, host.dp(42)));
        LinearLayout controls = new LinearLayout(host);
        controls.setOrientation(LinearLayout.VERTICAL);
        addChoice(controls, host.tr("Light", "Светлая"), "light");
        addChoice(controls, host.tr("Dark", "Темная"), "dark");
        addChoice(controls, host.tr("Custom", "Своя"), "custom");
        if ("custom".equals(host.themeMode)) {
            controls.addView(host.text(host.tr("Background", "Фон"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_BACKGROUND);
            controls.addView(host.text(host.tr("Accent", "Акцент"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_ACCENT);
            controls.addView(host.text(host.tr("Second accent", "Второй акцент"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_SECONDARY_ACCENT);
            controls.addView(host.text(host.tr("Text", "Текст"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_TEXT);
            Button outlineToggle = host.button(host.tr("Text outline: ", "Контур текста: ")
                    + host.tr(host.textOutlineEnabled ? "on" : "off",
                    host.textOutlineEnabled ? "вкл" : "выкл"));
            host.applySecondaryButtonStyle(outlineToggle);
            outlineToggle.setOnClickListener(view -> {
                host.textOutlineEnabled = !host.textOutlineEnabled;
                applyTheme(host.themeMode);
            });
            LinearLayout.LayoutParams outlineParams = new LinearLayout.LayoutParams(-1, host.dp(46));
            outlineParams.setMargins(0, host.dp(8), 0, host.dp(8));
            controls.addView(outlineToggle, outlineParams);
            if (host.textOutlineEnabled) {
                TextView outlineLabel = host.text(host.tr("Outline color", "Цвет контура"), 16, true);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, host.dp(32));
                labelParams.setMargins(0, host.dp(4), 0, host.dp(2));
                controls.addView(outlineLabel, labelParams);
                addColorButton(controls, COLOR_OUTLINE);
            }
            ScrollView scroll = new ScrollView(host);
            scroll.addView(controls, new ScrollView.LayoutParams(-1, -2));
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        } else {
            panel.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        }
        Button done = host.button(host.tr("Done", "Готово"));
        host.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            if (shade.getParent() != null) {
                host.overlayHost.removeView(shade);
            }
            host.updateMini();
        });
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, host.dp(48));
        doneParams.setMargins(0, host.dp(8), 0, 0);
        panel.addView(done, doneParams);
        if ("custom".equals(host.themeMode)) {
            int maxHeight = Math.min(host.dp(650),
                    host.getResources().getDisplayMetrics().heightPixels - host.dp(44));
            shade.addView(panel, host.centerParams(host.dp(340), maxHeight));
        } else {
            shade.addView(panel, host.centerParams(host.dp(340), -2));
        }
        host.overlayHost.addView(shade);
        host.updateMini();
    }

    private void addChoice(LinearLayout parent, String label, final String mode) {
        Button button = host.button(label);
        button.setTextSize(17.0f);
        button.setGravity(8388627);
        button.setPadding(host.dp(18), 0, host.dp(12), 0);
        if (mode.equals(host.themeMode)) {
            host.applyPrimaryButtonStyle(button);
        } else {
            host.applySecondaryButtonStyle(button);
        }
        button.setOnClickListener(view -> applyTheme(mode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(48));
        params.setMargins(0, host.dp(3), 0, host.dp(3));
        parent.addView(button, params);
    }

    private void addColorButton(LinearLayout parent, final int target) {
        int color = colorForTarget(target);
        Button button = host.button(colorHex(color));
        button.setTextColor(ThemeManager.readableOn(color));
        host.setSurface(button, color, false);
        button.setOnClickListener(view -> {
            host.overlayHost.removeAllViews();
            openColorPicker(target);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(36));
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        parent.addView(button, params);
    }

    private void openColorPicker(final int target) {
        final FrameLayout shade = host.shade();
        LinearLayout panel = host.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.text(colorTargetName(target), 22, true),
                new LinearLayout.LayoutParams(-1, host.dp(46)));
        final View preview = new View(host);
        preview.setBackgroundColor(colorForTarget(target));
        panel.addView(preview, new LinearLayout.LayoutParams(-1, host.dp(34)));

        ThemeColorWheelView wheel = new ThemeColorWheelView(
                host,
                colorForTarget(target),
                color -> {
                    if (target == COLOR_BACKGROUND) {
                        host.themeMode = "custom";
                        host.customBg = color;
                    } else if (target == COLOR_ACCENT) {
                        host.themeMode = "custom";
                        host.customFg = color;
                    } else if (target == COLOR_SECONDARY_ACCENT) {
                        host.themeMode = "custom";
                        host.customSecondaryAccent = color;
                    } else if (target == COLOR_OUTLINE) {
                        host.textOutlineColor = color;
                    } else {
                        host.customTextColor = color;
                    }
                    preview.setBackgroundColor(color);
                });
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(-1, host.dp(280));
        wheelParams.setMargins(0, host.dp(12), 0, host.dp(12));
        panel.addView(wheel, wheelParams);

        LinearLayout actions = host.row();
        Button back = host.button(host.tr("Back", "Назад"));
        back.setOnClickListener(view -> {
            host.overlayHost.removeView(shade);
            openDialog();
        });
        actions.addView(back, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        Button done = host.button(host.tr("Done", "Готово"));
        host.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> applyTheme(
                target == COLOR_BACKGROUND || target == COLOR_ACCENT
                        || target == COLOR_SECONDARY_ACCENT
                        ? "custom" : host.themeMode));
        actions.addView(done, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        panel.addView(actions);
        shade.addView(panel, host.centerParams(host.dp(340), -2));
        host.overlayHost.addView(shade);
    }

    private int colorForTarget(int target) {
        if (target == COLOR_BACKGROUND) {
            return host.customBg;
        }
        if (target == COLOR_ACCENT) {
            return host.customFg;
        }
        if (target == COLOR_SECONDARY_ACCENT) {
            return host.customSecondaryAccent;
        }
        if (target == COLOR_OUTLINE) {
            return effectiveOutlineColor();
        }
        return host.customTextColor != 0 ? host.customTextColor : host.primaryText;
    }

    private String colorTargetName(int target) {
        if (target == COLOR_BACKGROUND) {
            return host.tr("Background", "Фон");
        }
        if (target == COLOR_ACCENT) {
            return host.tr("Accent", "Акцент");
        }
        if (target == COLOR_SECONDARY_ACCENT) {
            return host.tr("Second accent", "Второй акцент");
        }
        if (target == COLOR_OUTLINE) {
            return host.tr("Outline color", "Цвет контура");
        }
        return host.tr("Text", "Текст");
    }

    void applyTextOutline(TextView text) {
        text.setShadowLayer(0.0f, 0.0f, 0.0f, Color.TRANSPARENT);
        if (text instanceof OutlinedTextView) {
            boolean lightTheme = "light".equals(host.themeMode);
            boolean darkTheme = "dark".equals(host.themeMode);
            float width = host.getResources().getDisplayMetrics().density
                    * (lightTheme || darkTheme ? 1.0f : 0.35f);
            ((OutlinedTextView) text).setTextOutline(
                    lightTheme || darkTheme || host.textOutlineEnabled,
                    lightTheme ? Color.WHITE
                            : darkTheme ? Color.BLACK : effectiveOutlineColor(), width);
        } else if (text instanceof OutlinedButton) {
            boolean lightTheme = "light".equals(host.themeMode);
            boolean darkTheme = "dark".equals(host.themeMode);
            float width = host.getResources().getDisplayMetrics().density
                    * (lightTheme || darkTheme ? 1.0f : 0.35f);
            ((OutlinedButton) text).setTextOutline(
                    lightTheme || darkTheme || host.textOutlineEnabled,
                    lightTheme ? Color.WHITE
                            : darkTheme ? Color.BLACK : effectiveOutlineColor(), width);
        }
    }

    private int effectiveOutlineColor() {
        return host.textOutlineColor != 0
                ? host.textOutlineColor : ThemeManager.readableOn(host.primaryText);
    }

    private void applyTheme(String mode) {
        host.themeMode = mode;
        host.dark = isDarkTheme(mode, host.customBg);
        host.saveState();
        host.refreshPlaybackAppearance();
        if (host.overlayHost != null) {
            host.overlayHost.removeAllViews();
        }
        host.rebuildUiForTheme();
        openDialog();
        launcherUpdatePending = true;
    }

    void onHostStopped() {
        if (!launcherUpdatePending) {
            return;
        }
        launcherUpdatePending = false;
        updateLauncherIcon();
    }

    void updateLauncherIcon() {
        PackageManager packageManager = host.getPackageManager();
        boolean useDark = isDarkTheme(host.themeMode, host.customBg);
        ComponentName selected = LauncherComponents.forPalette(
                host, host.themeMode, useDark, host.purple, host.yellow);
        try {
            packageManager.setComponentEnabledSetting(
                    selected,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            for (ComponentName component : LauncherComponents.all(host)) {
                if (!component.equals(selected)) {
                    packageManager.setComponentEnabledSetting(
                            component,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                }
            }
        } catch (RuntimeException ignored) {
            // A launcher may reject alias changes while the task is visible.
        }
    }

    private void updateTaskPreview() {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        try {
            host.setTaskDescription(new ActivityManager.TaskDescription(
                    "MP3 Player Voltune", launcherPreviewIcon(), host.bg));
        } catch (RuntimeException ignored) {
        }
    }

    private Bitmap launcherPreviewIcon() {
        boolean useDark = isDarkTheme(host.themeMode, host.customBg);
        ComponentName launcher = LauncherComponents.forPalette(
                host, host.themeMode, useDark, host.purple, host.yellow);
        return AppIconRenderer.renderLauncherPreview(
                host, launcher, host.bg, host.purple, host.yellow,
                Math.max(1, host.dp(64)));
    }

    private String colorHex(int color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    static boolean isDarkTheme(String themeMode, int customBackground) {
        return "dark".equals(themeMode)
                || ("custom".equals(themeMode) && ThemeManager.isDarkColor(customBackground));
    }

    static int mixColor(int first, int second, float amount) {
        return ThemeManager.mixColor(first, second, amount);
    }
}
