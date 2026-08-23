package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

final class ParticleSettingsController {
    private final MainActivityCore host;

    ParticleSettingsController(MainActivityCore host) {
        this.host = host;
    }

    void openDialog() {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(
                        host.tr("Particle settings", "Настройка частиц")),
                host.uiFactory.dialogTitleParams());
        addSlider(panel, host.tr("Frequency", "Частота"), 10, 100, host.appearanceState.particleFrequency,
                value -> host.appearanceState.particleFrequency = value);
        addSlider(panel, host.tr("Size", "Размер"), 60, 150, host.appearanceState.particleSize,
                value -> host.appearanceState.particleSize = value);
        addSlider(panel, host.tr("Lifetime", "Время существования"), 50, 180, host.appearanceState.particleLifetime,
                value -> host.appearanceState.particleLifetime = value);
        addColorButton(panel, true);
        addColorButton(panel, false);

        Button reset = host.uiFactory.button(host.tr("Restore defaults", "По умолчанию"));
        host.uiFactory.applySecondaryButtonStyle(reset);
        reset.setOnClickListener(view -> {
            host.appearanceState.particleFrequency = 45;
            host.appearanceState.particleSize = 100;
            host.appearanceState.particleLifetime = 100;
            host.appearanceState.particlePrimaryColor = 0;
            host.appearanceState.particleSecondaryColor = 0;
            host.saveState();
            host.refreshParticleSettings();
            host.overlayHost.removeView(shade);
            openDialog();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, host.dp(46));
        resetParams.setMargins(0, host.dp(8), 0, 0);
        panel.addView(reset, resetParams);

        Button close = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(close);
        close.setOnClickListener(view -> {
            host.saveState();
            host.refreshParticleSettings();
            host.overlayHost.removeView(shade);
            host.refreshSettingsLabels();
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, host.dp(50));
        closeParams.setMargins(0, host.dp(10), 0, 0);
        panel.addView(close, closeParams);
        shade.addView(panel, host.centerParams(host.dp(340), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private void addColorButton(LinearLayout panel, boolean primary) {
        int color = selectedColor(primary);
        String name = primary
                ? host.tr("Primary color", "Основной цвет")
                : host.tr("Secondary color", "Дополнительный цвет");
        Button button = host.uiFactory.button(name + ": " + colorHex(color));
        button.setTextColor(ThemeManager.readableOn(color));
        host.uiFactory.setSurface(button, color, false);
        button.setOnClickListener(view -> {
            host.overlayHost.removeAllViews();
            openColorPicker(primary);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(48));
        params.setMargins(0, host.dp(3), 0, host.dp(3));
        panel.addView(button, params);
    }

    private void openColorPicker(boolean primary) {
        int originalColor = primary ? host.appearanceState.particlePrimaryColor : host.appearanceState.particleSecondaryColor;
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(primary
                        ? host.tr("Primary particle color", "Основной цвет частиц")
                        : host.tr("Secondary particle color", "Дополнительный цвет частиц"),
                21), host.uiFactory.dialogTitleParams());

        View preview = new View(host);
        preview.setBackgroundColor(selectedColor(primary));
        panel.addView(preview, new LinearLayout.LayoutParams(-1, host.dp(32)));

        ThemeColorWheelView wheel = new ThemeColorWheelView(host, selectedColor(primary), color -> {
            if (primary) {
                host.appearanceState.particlePrimaryColor = color;
            } else {
                host.appearanceState.particleSecondaryColor = color;
            }
            preview.setBackgroundColor(color);
            host.refreshParticleSettings();
        });
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(-1, host.dp(260));
        wheelParams.setMargins(0, host.dp(10), 0, host.dp(10));
        panel.addView(wheel, wheelParams);

        LinearLayout actions = host.uiFactory.row();
        Button cancel = host.uiFactory.button(host.tr("Cancel", "Отмена"));
        cancel.setOnClickListener(view -> {
            if (primary) {
                host.appearanceState.particlePrimaryColor = originalColor;
            } else {
                host.appearanceState.particleSecondaryColor = originalColor;
            }
            host.refreshParticleSettings();
            host.overlayHost.removeView(shade);
            openDialog();
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, host.dp(50), 1.0f));

        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            host.saveState();
            host.refreshParticleSettings();
            host.overlayHost.removeView(shade);
            openDialog();
        });
        actions.addView(done, new LinearLayout.LayoutParams(0, host.dp(50), 1.0f));
        panel.addView(actions);

        shade.addView(panel, host.centerParams(host.dp(340), -2));
        host.overlayHost.addView(shade);
    }

    private int selectedColor(boolean primary) {
        int configured = primary ? host.appearanceState.particlePrimaryColor : host.appearanceState.particleSecondaryColor;
        if (configured != 0) {
            return configured;
        }
        return primary ? host.purple : host.yellow;
    }

    private static String colorHex(int color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private void addSlider(LinearLayout panel, String name, int minimum, int maximum, int initial,
            ValueChanged listener) {
        int value = Math.max(minimum, Math.min(maximum, initial));
        TextView label = host.uiFactory.text(name + ": " + value + "%", 15, false);
        panel.addView(label, new LinearLayout.LayoutParams(-1, host.dp(34)));
        SeekBar seek = new SeekBar(host);
        seek.setMax(maximum - minimum);
        seek.setProgress(value - minimum);
        host.uiFactory.applySeekBarColors(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int selected = minimum + progress;
                label.setText(name + ": " + selected + "%");
                listener.changed(selected);
                host.refreshParticleSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                host.saveState();
            }
        });
        panel.addView(seek, new LinearLayout.LayoutParams(-1, host.dp(44)));
    }

    private interface ValueChanged {
        void changed(int value);
    }
}
