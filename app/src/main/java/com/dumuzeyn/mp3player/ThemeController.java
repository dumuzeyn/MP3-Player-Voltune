package com.dumuzeyn.mp3player;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
        host.appearanceState.themeMode = prefs.getString(THEME, "light");
        if (!"light".equals(host.appearanceState.themeMode)
                && !"dark".equals(host.appearanceState.themeMode)
                && !"system".equals(host.appearanceState.themeMode)
                && !"custom".equals(host.appearanceState.themeMode)) {
            host.appearanceState.themeMode = "light";
        }
        host.appearanceState.customBg = prefs.getInt(CUSTOM_BG, Color.WHITE);
        host.appearanceState.customFg = prefs.getInt(CUSTOM_FG, Color.BLACK);
        host.appearanceState.customSecondaryAccent = prefs.getInt(
                CUSTOM_SECONDARY_ACCENT, Color.rgb(255, 208, 0));
    }

    String themeName() {
        if ("dark".equals(host.appearanceState.themeMode)) {
            return host.tr("Dark", "Темная");
        }
        if ("custom".equals(host.appearanceState.themeMode)) {
            return host.tr("Custom", "Своя");
        }
        if ("system".equals(host.appearanceState.themeMode)) {
            return host.tr("System", "Системная");
        }
        return host.tr("Light", "Светлая");
    }

    void applyPalette() {
        host.appearanceState.dark = isDarkTheme(host.appearanceState.themeMode,
                host.appearanceState.customBg, isSystemDark(host));
        if ("custom".equals(host.appearanceState.themeMode)) {
            host.bg = host.appearanceState.customBg;
            host.fg = host.appearanceState.customFg;
            host.primaryText = host.appearanceState.customFg;
            host.secondaryText = mixColor(host.appearanceState.customFg, host.appearanceState.customBg, 0.58f);
            host.card = mixColor(host.appearanceState.customBg, host.appearanceState.customFg, host.appearanceState.dark ? 0.92f : 0.96f);
            host.cardStroke = mixColor(host.appearanceState.customFg, host.appearanceState.customBg, 0.18f);
            host.purple = host.appearanceState.customFg;
            host.purpleDark = mixColor(host.appearanceState.customFg, host.appearanceState.customBg, 0.82f);
            host.purpleSoft = mixColor(host.appearanceState.customFg, host.appearanceState.customBg, 0.18f);
            host.yellow = host.appearanceState.customSecondaryAccent;
            host.yellowDark = mixColor(host.appearanceState.customSecondaryAccent, host.appearanceState.customBg, 0.82f);
            host.yellowSoft = mixColor(host.appearanceState.customSecondaryAccent, host.appearanceState.customBg, 0.18f);
        } else if (host.appearanceState.dark) {
            host.bg = host.getColor(R.color.voltune_background_dark);
            host.fg = host.getColor(R.color.voltune_text_dark);
            host.primaryText = host.getColor(R.color.voltune_text_dark);
            host.secondaryText = host.getColor(R.color.voltune_text_secondary_dark);
            host.card = host.getColor(R.color.voltune_surface_dark);
            host.cardStroke = host.getColor(R.color.voltune_stroke_dark);
            host.purple = host.getColor(R.color.voltune_primary_dark);
            host.purpleDark = host.getColor(R.color.voltune_primary_strong_dark);
            host.purpleSoft = host.getColor(R.color.voltune_primary_soft_dark);
            host.yellow = host.getColor(R.color.voltune_secondary_dark);
            host.yellowDark = host.getColor(R.color.voltune_secondary_strong);
            host.yellowSoft = host.getColor(R.color.voltune_secondary_soft_dark);
        } else {
            host.bg = host.getColor(R.color.voltune_background_light);
            host.fg = host.getColor(R.color.voltune_text_light);
            host.primaryText = host.getColor(R.color.voltune_text_light);
            host.secondaryText = host.getColor(R.color.voltune_text_secondary_light);
            host.card = host.getColor(R.color.voltune_surface_light);
            host.cardStroke = host.getColor(R.color.voltune_stroke_light);
            host.purple = host.getColor(R.color.voltune_primary_light);
            host.purpleDark = host.getColor(R.color.voltune_primary_strong_light);
            host.purpleSoft = host.getColor(R.color.voltune_primary_soft_light);
            host.yellow = host.getColor(R.color.voltune_secondary_light);
            host.yellowDark = host.getColor(R.color.voltune_secondary_strong);
            host.yellowSoft = host.getColor(R.color.voltune_secondary_soft_light);
        }
        if ("custom".equals(host.appearanceState.themeMode) && host.appearanceState.customTextColor != 0) {
            host.fg = host.appearanceState.customTextColor;
            host.primaryText = host.appearanceState.customTextColor;
            host.secondaryText = mixColor(host.appearanceState.customTextColor, host.bg, 0.58f);
            if (ThemeContrastPolicy.requiresOutline(host.appearanceState.customTextColor, host.bg)) {
                host.appearanceState.textOutlineEnabled = true;
            }
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
            int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                lightBars |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            host.getWindow().getDecorView().setSystemUiVisibility(
                    host.appearanceState.dark ? 0 : lightBars);
        }
        updateTaskPreview();
    }

    void openDialog() {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(host.tr("Theme", "Тема")),
                host.uiFactory.dialogTitleParams());
        LinearLayout controls = new LinearLayout(host);
        controls.setOrientation(LinearLayout.VERTICAL);
        addChoice(controls, host.tr("Light", "Светлая"), "light");
        addChoice(controls, host.tr("Dark", "Темная"), "dark");
        addChoice(controls, host.tr("System", "Системная"), "system");
        addChoice(controls, host.tr("Custom", "Своя"), "custom");
        if ("custom".equals(host.appearanceState.themeMode)) {
            controls.addView(host.uiFactory.text(host.tr("Background", "Фон"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_BACKGROUND);
            controls.addView(host.uiFactory.text(host.tr("Accent", "Акцент"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_ACCENT);
            controls.addView(host.uiFactory.text(host.tr("Second accent", "Второй акцент"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_SECONDARY_ACCENT);
            controls.addView(host.uiFactory.text(host.tr("Text", "Текст"), 16, true),
                    new LinearLayout.LayoutParams(-1, host.dp(30)));
            addColorButton(controls, COLOR_TEXT);
            Button outlineToggle = host.uiFactory.button(host.tr("Text outline: ", "Контур текста: ")
                    + host.tr(host.appearanceState.textOutlineEnabled ? "on" : "off",
                    host.appearanceState.textOutlineEnabled ? "вкл" : "выкл"));
            host.uiFactory.applySecondaryButtonStyle(outlineToggle);
            outlineToggle.setOnClickListener(view -> {
                host.appearanceState.textOutlineEnabled = !host.appearanceState.textOutlineEnabled;
                applyTheme(host.appearanceState.themeMode);
            });
            LinearLayout.LayoutParams outlineParams = new LinearLayout.LayoutParams(-1, host.dp(46));
            outlineParams.setMargins(0, host.dp(8), 0, host.dp(8));
            controls.addView(outlineToggle, outlineParams);
            if (host.appearanceState.customTextColor != 0
                    && ThemeContrastPolicy.requiresOutline(host.appearanceState.customTextColor, host.bg)) {
                TextView contrastHint = host.uiFactory.text(host.tr(
                        "The outline is enabled automatically because the selected text color "
                                + "has low contrast. Your color is unchanged.",
                        "Контур включён автоматически из-за низкого контраста. "
                                + "Выбранный цвет текста не изменён."), 13, false);
                controls.addView(contrastHint, new LinearLayout.LayoutParams(-1, -2));
            }
            if (host.appearanceState.textOutlineEnabled) {
                TextView outlineLabel = host.uiFactory.text(host.tr("Outline color", "Цвет контура"), 16, true);
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
        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            if (shade.getParent() != null) {
                host.overlayHost.removeView(shade);
            }
            host.playerUiController.updateMini();
        });
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, host.dp(48));
        doneParams.setMargins(0, host.dp(8), 0, 0);
        panel.addView(done, doneParams);
        if ("custom".equals(host.appearanceState.themeMode)) {
            int maxHeight = Math.min(host.dp(650),
                    host.getResources().getDisplayMetrics().heightPixels - host.dp(44));
            shade.addView(panel, host.centerParams(host.dp(340), maxHeight));
        } else {
            shade.addView(panel, host.centerParams(host.dp(340), -2));
        }
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private void addChoice(LinearLayout parent, String label, final String mode) {
        Button button = host.uiFactory.button(label);
        button.setTextSize(17.0f);
        button.setGravity(8388627);
        button.setPadding(host.dp(18), 0, host.dp(12), 0);
        if (mode.equals(host.appearanceState.themeMode)) {
            host.uiFactory.applyPrimaryButtonStyle(button);
        } else {
            host.uiFactory.applySecondaryButtonStyle(button);
        }
        button.setOnClickListener(view -> applyTheme(mode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(48));
        params.setMargins(0, host.dp(3), 0, host.dp(3));
        parent.addView(button, params);
    }

    private void addColorButton(LinearLayout parent, final int target) {
        int color = colorForTarget(target);
        Button button = host.uiFactory.button(colorHex(color));
        button.setTextColor(ThemeManager.readableOn(color));
        host.uiFactory.setSurface(button, color, false);
        button.setOnClickListener(view -> {
            host.overlayHost.removeAllViews();
            openColorPicker(target);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(36));
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        parent.addView(button, params);
    }

    private void openColorPicker(final int target) {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(colorTargetName(target)),
                host.uiFactory.dialogTitleParams());
        final View preview = new View(host);
        preview.setBackgroundColor(colorForTarget(target));
        panel.addView(preview, new LinearLayout.LayoutParams(-1, host.dp(34)));

        ThemeColorWheelView wheel = new ThemeColorWheelView(
                host,
                colorForTarget(target),
                color -> {
                    if (target == COLOR_BACKGROUND) {
                        host.appearanceState.themeMode = "custom";
                        host.appearanceState.customBg = color;
                    } else if (target == COLOR_ACCENT) {
                        host.appearanceState.themeMode = "custom";
                        host.appearanceState.customFg = color;
                    } else if (target == COLOR_SECONDARY_ACCENT) {
                        host.appearanceState.themeMode = "custom";
                        host.appearanceState.customSecondaryAccent = color;
                    } else if (target == COLOR_OUTLINE) {
                        host.appearanceState.textOutlineColor = color;
                    } else {
                        host.appearanceState.customTextColor = color;
                    }
                    preview.setBackgroundColor(color);
                });
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(-1, host.dp(280));
        wheelParams.setMargins(0, host.dp(12), 0, host.dp(12));
        panel.addView(wheel, wheelParams);

        LinearLayout actions = host.uiFactory.row();
        Button back = host.uiFactory.button(host.tr("Back", "Назад"));
        back.setOnClickListener(view -> {
            host.overlayHost.removeView(shade);
            openDialog();
        });
        actions.addView(back, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> applyTheme(
                target == COLOR_BACKGROUND || target == COLOR_ACCENT
                        || target == COLOR_SECONDARY_ACCENT
                        ? "custom" : host.appearanceState.themeMode));
        actions.addView(done, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        panel.addView(actions);
        shade.addView(panel, host.centerParams(host.dp(340), -2));
        host.overlayHost.addView(shade);
    }

    private int colorForTarget(int target) {
        if (target == COLOR_BACKGROUND) {
            return host.appearanceState.customBg;
        }
        if (target == COLOR_ACCENT) {
            return host.appearanceState.customFg;
        }
        if (target == COLOR_SECONDARY_ACCENT) {
            return host.appearanceState.customSecondaryAccent;
        }
        if (target == COLOR_OUTLINE) {
            return effectiveOutlineColor();
        }
        return host.appearanceState.customTextColor != 0 ? host.appearanceState.customTextColor : host.primaryText;
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
            boolean lightTheme = "light".equals(host.appearanceState.themeMode);
            boolean darkTheme = "dark".equals(host.appearanceState.themeMode);
            float width = host.getResources().getDisplayMetrics().density
                    * (lightTheme || darkTheme ? 0.65f : 0.25f);
            ((OutlinedTextView) text).setTextOutline(
                    lightTheme || darkTheme || host.appearanceState.textOutlineEnabled,
                    lightTheme ? Color.WHITE
                            : darkTheme ? Color.BLACK : effectiveOutlineColor(), width);
        } else if (text instanceof OutlinedButton) {
            boolean lightTheme = "light".equals(host.appearanceState.themeMode);
            boolean darkTheme = "dark".equals(host.appearanceState.themeMode);
            float width = host.getResources().getDisplayMetrics().density
                    * (lightTheme || darkTheme ? 0.65f : 0.25f);
            ((OutlinedButton) text).setTextOutline(
                    lightTheme || darkTheme || host.appearanceState.textOutlineEnabled,
                    lightTheme ? Color.WHITE
                            : darkTheme ? Color.BLACK : effectiveOutlineColor(), width);
        }
    }

    private int effectiveOutlineColor() {
        return host.appearanceState.textOutlineColor != 0
                ? host.appearanceState.textOutlineColor : ThemeManager.readableOn(host.primaryText);
    }

    private void applyTheme(String mode) {
        host.appearanceState.themeMode = mode;
        host.appearanceState.dark = isDarkTheme(mode, host.appearanceState.customBg,
                isSystemDark(host));
        host.saveState();
        host.refreshPlaybackAppearance();
        if (host.overlayHost != null) {
            host.overlayHost.removeAllViews();
        }
        host.rebuildUiForTheme();
        openDialog();
        launcherUpdatePending = true;
        updateLauncherIcon();
    }

    void onHostStopped() {
        if (!launcherUpdatePending) {
            return;
        }
        launcherUpdatePending = false;
        updateLauncherIcon();
    }

    void syncLauncherIcon() {
        launcherUpdatePending = true;
        updateLauncherIcon();
    }

    void updateLauncherIcon() {
        boolean useDark = isDarkTheme(host.appearanceState.themeMode,
                host.appearanceState.customBg, isSystemDark(host));
        ComponentName selected = LauncherComponents.forThemeState(
                host, host.appearanceState.themeMode, useDark,
                host.appearanceState.customBg);
        try {
            LauncherComponents.apply(host, selected);
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
        return AppIconRenderer.renderPreview(host, host.bg, host.purple, host.yellow,
                Math.max(1, host.dp(64)));
    }

    private String colorHex(int color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    static boolean isDarkTheme(String themeMode, int customBackground, boolean systemDark) {
        return "dark".equals(themeMode)
                || ("system".equals(themeMode) && systemDark)
                || ("custom".equals(themeMode) && ThemeManager.isDarkColor(customBackground));
    }

    static boolean isSystemDark(android.content.Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    static int mixColor(int first, int second, float amount) {
        return ThemeManager.mixColor(first, second, amount);
    }
}
