package com.dumuzeyn.mp3player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BackgroundSettingsController {
    static final int MODE_SOLID = 0;
    static final int MODE_GRADIENT = 1;
    static final int MODE_MEDIA = 2;
    private static final int REQUEST_MAIN_MEDIA = 4301;
    private static final int REQUEST_PLAYER_MEDIA = 4302;
    private static final int MAIN_SOLID = 0;
    private static final int MAIN_START = 1;
    private static final int MAIN_END = 2;
    private static final int PLAYER_SOLID = 3;
    private static final int PLAYER_START = 4;
    private static final int PLAYER_END = 5;

    private final MainActivityCore host;
    private final ExecutorService validatorExecutor = Executors.newSingleThreadExecutor();

    BackgroundSettingsController(MainActivityCore host) {
        this.host = host;
    }

    void openDialog() {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(12));
        TextView dialogTitle = host.uiFactory.dialogTitle(host.tr("Background", "Фон"));
        panel.addView(dialogTitle, host.uiFactory.dialogTitleParams());

        ScrollView scroll = new ScrollView(host);
        LinearLayout rows = new LinearLayout(host);
        rows.setOrientation(LinearLayout.VERTICAL);
        addTarget(rows, true, shade);
        View divider = host.uiFactory.lineView();
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, host.dp(1));
        dividerParams.setMargins(0, host.dp(10), 0, host.dp(10));
        rows.addView(divider, dividerParams);
        addTarget(rows, false, shade);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            host.saveState();
            host.overlayHost.removeView(shade);
            host.rebuildUi();
        });
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, host.dp(48));
        doneParams.setMargins(0, host.dp(8), 0, 0);
        panel.addView(done, doneParams);

        int maxHeight = Math.min(host.dp(650),
                host.getResources().getDisplayMetrics().heightPixels - host.dp(44));
        int contentWidth = host.dp(360) - panel.getPaddingLeft() - panel.getPaddingRight();
        rows.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        dialogTitle.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int desiredHeight = panel.getPaddingTop() + panel.getPaddingBottom()
                + dialogTitle.getMeasuredHeight() + rows.getMeasuredHeight() + host.dp(56);
        shade.addView(panel, host.centerParams(host.dp(360), Math.min(maxHeight, desiredHeight)));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_MAIN_MEDIA && requestCode != REQUEST_PLAYER_MEDIA) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        Uri uri = data.getData();
        final boolean main = requestCode == REQUEST_MAIN_MEDIA;
        validatorExecutor.execute(() -> {
            BackgroundMediaValidator.Result result = BackgroundMediaValidator.validate(host, uri);
            host.runOnUiThread(() -> {
                if (!result.valid) {
                    Toast.makeText(host, host.tr("This file is not a safe supported image.",
                            "Файл не является безопасным поддерживаемым изображением."),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    host.getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    Toast.makeText(host, host.tr("Permanent access to the image was not granted.",
                            "Постоянный доступ к изображению не предоставлен."),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (result.heavy) {
                    host.showActionPanel(
                            host.tr("Heavy animated background", "Тяжёлый анимированный фон"),
                            host.tr("This image may increase memory use and battery drain. "
                                            + "Use it anyway?",
                                    "Это изображение может увеличить расход памяти и заряда. "
                                            + "Всё равно использовать?"),
                            host.tr("Cancel", "Отмена"), host.tr("Use", "Использовать"),
                            true, () -> applyMedia(main, uri));
                } else {
                    applyMedia(main, uri);
                }
            });
        });
        return true;
    }

    private void applyMedia(boolean main, Uri uri) {
        if (main) {
            host.appearanceState.mainBackgroundMediaUri = uri.toString();
            host.appearanceState.mainBackgroundMode = MODE_MEDIA;
        } else {
            host.appearanceState.playerBackgroundMediaUri = uri.toString();
            host.appearanceState.playerBackgroundMode = MODE_MEDIA;
        }
        host.saveState();
        host.rebuildUi();
        openDialog();
    }

    void close() {
        validatorExecutor.shutdownNow();
    }

    private void addTarget(LinearLayout rows, boolean main, FrameLayout shade) {
        rows.addView(host.uiFactory.text(main
                        ? host.tr("Main application", "Основное приложение")
                        : host.tr("Full player", "Большой плеер"), 18, true),
                new LinearLayout.LayoutParams(-1, host.dp(38)));
        int mode = main ? host.appearanceState.mainBackgroundMode : host.appearanceState.playerBackgroundMode;
        addAction(rows, host.tr("Type: ", "Тип: ") + modeName(mode), () -> {
            setMode(main, (mode + 1) % 3);
            saveAndReopen(shade);
        });
        if (mode == MODE_SOLID) {
            int color = main ? resolvedSolid(host.appearanceState.mainSolidBackground) : resolvedSolid(host.appearanceState.playerSolidBackground);
            addColor(rows, host.tr("Color", "Цвет"), main ? MAIN_SOLID : PLAYER_SOLID,
                    color, shade);
        } else if (mode == MODE_GRADIENT) {
            addColor(rows, host.tr("Color 1", "Цвет 1"), main ? MAIN_START : PLAYER_START,
                    main ? host.appearanceState.mainGradientStart : host.appearanceState.playerGradientStart, shade);
            addColor(rows, host.tr("Color 2", "Цвет 2"), main ? MAIN_END : PLAYER_END,
                    main ? host.appearanceState.mainGradientEnd : host.appearanceState.playerGradientEnd, shade);
        } else {
            String uri = main ? host.appearanceState.mainBackgroundMediaUri : host.appearanceState.playerBackgroundMediaUri;
            addAction(rows, uri.isEmpty()
                    ? host.tr("Choose visual media", "Выбрать медиафон")
                    : host.tr("Replace visual media", "Заменить медиафон"),
                    () -> openMediaPicker(main));
            addBlur(rows, main);
        }
    }

    private void addBlur(LinearLayout rows, boolean main) {
        int blur = main ? host.appearanceState.mainBackgroundBlur : host.appearanceState.playerBackgroundBlur;
        TextView label = host.uiFactory.text(host.tr("Blur: ", "Размытие: ") + blur + "%", 15, false);
        rows.addView(label, new LinearLayout.LayoutParams(-1, host.dp(30)));
        SeekBar seek = new SeekBar(host);
        seek.setMax(100);
        seek.setProgress(blur);
        host.uiFactory.applySeekBarColors(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                label.setText(host.tr("Blur: ", "Размытие: ") + progress + "%");
                if (main) host.appearanceState.mainBackgroundBlur = progress;
                else host.appearanceState.playerBackgroundBlur = progress;
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                host.saveState();
            }
        });
        rows.addView(seek, new LinearLayout.LayoutParams(-1, host.dp(42)));
    }

    private void openMediaPicker(boolean main) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        host.startActivityForResult(intent, main ? REQUEST_MAIN_MEDIA : REQUEST_PLAYER_MEDIA);
    }

    private void addColor(LinearLayout parent, String label, int target, int color,
            FrameLayout parentShade) {
        Button button = host.uiFactory.button(label + "  " + colorHex(color));
        button.setTextColor(ThemeManager.readableOn(color));
        host.uiFactory.setSurface(button, color, false);
        button.setOnClickListener(view -> {
            host.overlayHost.removeView(parentShade);
            openColorPicker(label, target, color);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(46));
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        parent.addView(button, params);
    }

    private void openColorPicker(String title, int target, int initialColor) {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(title, 21),
                host.uiFactory.dialogTitleParams());
        View preview = new View(host);
        preview.setBackgroundColor(initialColor);
        panel.addView(preview, new LinearLayout.LayoutParams(-1, host.dp(34)));
        ThemeColorWheelView wheel = new ThemeColorWheelView(host, initialColor, color -> {
            setColor(target, color);
            preview.setBackgroundColor(color);
        });
        panel.addView(wheel, new LinearLayout.LayoutParams(-1, host.dp(280)));
        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            host.saveState();
            host.overlayHost.removeView(shade);
            openDialog();
        });
        panel.addView(done, new LinearLayout.LayoutParams(-1, host.dp(50)));
        shade.addView(panel, host.centerParams(host.dp(350), -2));
        host.overlayHost.addView(shade);
    }

    private void addAction(LinearLayout parent, String label, Runnable action) {
        Button button = host.uiFactory.button(label);
        host.uiFactory.applySecondaryButtonStyle(button);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(46));
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        parent.addView(button, params);
    }

    private void saveAndReopen(FrameLayout shade) {
        host.saveState();
        host.overlayHost.removeView(shade);
        openDialog();
    }

    private void setMode(boolean main, int mode) {
        if (main) host.appearanceState.mainBackgroundMode = mode;
        else host.appearanceState.playerBackgroundMode = mode;
    }

    private void setColor(int target, int color) {
        if (target == MAIN_SOLID) host.appearanceState.mainSolidBackground = color;
        else if (target == MAIN_START) host.appearanceState.mainGradientStart = color;
        else if (target == MAIN_END) host.appearanceState.mainGradientEnd = color;
        else if (target == PLAYER_SOLID) host.appearanceState.playerSolidBackground = color;
        else if (target == PLAYER_START) host.appearanceState.playerGradientStart = color;
        else host.appearanceState.playerGradientEnd = color;
    }

    private int resolvedSolid(int color) {
        return color == 0 ? host.bg : color;
    }

    private String modeName(int mode) {
        if (mode == MODE_GRADIENT) return host.tr("gradient", "градиент");
        if (mode == MODE_MEDIA) return host.tr("visual media", "медиафон");
        return host.tr("solid", "однотонный");
    }

    private String colorHex(int color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
