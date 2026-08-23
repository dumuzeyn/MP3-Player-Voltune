package com.dumuzeyn.mp3player;

import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class EqualizerController {
    static final String PREFS = "audio_effects";
    static final String ENABLED = "equalizer_enabled";
    static final String BAND_PREFIX = "equalizer_band_";
    private static final String CUSTOM_BAND_PREFIX = "equalizer_custom_band_";
    private static final String ACTIVE_PRESET = "equalizer_preset";
    private static final String PRESET_CUSTOM = "custom";
    static final int BAND_COUNT = 5;
    private static final String[] BAND_NAMES = {"60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"};
    private static final int MIN_DB = -12;
    private static final int MAX_DB = 12;
    private static final String[] PRESET_IDS = {
            "flat", "bass", "vocal", "rock", "electronic", "classical", PRESET_CUSTOM
    };
    private static final int[][] PRESET_LEVELS = {
            {0, 0, 0, 0, 0},
            {7, 5, 1, -1, 1},
            {-3, -1, 4, 5, 2},
            {5, 2, -2, 3, 5},
            {6, 3, 0, 3, 6},
            {3, 1, -1, 2, 4}
    };
    private final MainActivityCore host;
    private Button playerButton;

    EqualizerController(MainActivityCore host) {
        this.host = host;
    }

    Button createPlayerButton() {
        Button button = host.uiFactory.button(host.tr("Equalizer ≋", "Эквалайзер ≋"));
        button.setSingleLine(true);
        button.setTextSize(14.0f);
        button.setOnClickListener(view -> openDialog());
        this.playerButton = button;
        refreshButton();
        return button;
    }

    void openDialog() {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(host.tr("Equalizer", "Эквалайзер")),
                host.uiFactory.dialogTitleParams());

        Button enabled = host.uiFactory.button(enabled()
                ? host.tr("Enabled", "Включён")
                : host.tr("Disabled", "Выключен"));
        styleToggle(enabled);
        enabled.setOnClickListener(view -> {
            setEnabled(!enabled());
            enabled.setText(enabled()
                    ? host.tr("Enabled", "Включён")
                    : host.tr("Disabled", "Выключен"));
            styleToggle(enabled);
        });
        panel.addView(enabled, new LinearLayout.LayoutParams(-1, host.dp(48)));

        Button preset = host.uiFactory.button(host.tr("Profile: ", "Профиль: ") + presetName(activePreset()));
        host.uiFactory.applySecondaryButtonStyle(preset);
        preset.setOnClickListener(view -> {
            host.overlayHost.removeView(shade);
            openPresetDialog();
        });
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(-1, host.dp(46));
        presetParams.setMargins(0, host.dp(5), 0, host.dp(5));
        panel.addView(preset, presetParams);

        for (int band = 0; band < BAND_COUNT; band++) {
            addBandControl(panel, band);
        }

        Button reset = host.uiFactory.button(host.tr("Reset bands", "Сбросить полосы"));
        host.uiFactory.applySecondaryButtonStyle(reset);
        reset.setOnClickListener(view -> {
            SharedPreferences.Editor editor = prefs().edit();
            for (int band = 0; band < BAND_COUNT; band++) {
                editor.putInt(BAND_PREFIX + band, 0);
                editor.putInt(CUSTOM_BAND_PREFIX + band, 0);
            }
            editor.putString(ACTIVE_PRESET, PRESET_CUSTOM);
            editor.apply();
            dispatchSettings();
            host.overlayHost.removeView(shade);
            openDialog();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, host.dp(48));
        resetParams.setMargins(0, host.dp(8), 0, 0);
        panel.addView(reset, resetParams);

        shade.addView(panel, host.centerParams(host.dp(340), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private void addBandControl(LinearLayout panel, int band) {
        int value = bandLevel(band);
        TextView label = host.uiFactory.text(bandLabel(band, value), 14, false);
        panel.addView(label, new LinearLayout.LayoutParams(-1, host.dp(28)));
        SeekBar seek = new SeekBar(host);
        seek.setMax(MAX_DB - MIN_DB);
        seek.setProgress(value - MIN_DB);
        host.uiFactory.applySeekBarColors(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int level = progress + MIN_DB;
                label.setText(bandLabel(band, level));
                prefs().edit()
                        .putString(ACTIVE_PRESET, PRESET_CUSTOM)
                        .putInt(BAND_PREFIX + band, level)
                        .putInt(CUSTOM_BAND_PREFIX + band, level)
                        .apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                dispatchSettings();
            }
        });
        panel.addView(seek, new LinearLayout.LayoutParams(-1, host.dp(38)));
    }

    private String bandLabel(int band, int level) {
        return BAND_NAMES[band] + "  " + (level > 0 ? "+" : "") + level + " dB";
    }

    private void openPresetDialog() {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(
                        host.tr("Equalizer profile", "Профиль эквалайзера"), 21),
                host.uiFactory.dialogTitleParams());
        String selected = activePreset();
        for (String presetId : PRESET_IDS) {
            Button choice = host.uiFactory.button(presetName(presetId));
            if (presetId.equals(selected)) {
                host.uiFactory.applyPrimaryButtonStyle(choice);
            } else {
                host.uiFactory.applySecondaryButtonStyle(choice);
            }
            choice.setOnClickListener(view -> {
                applyPreset(presetId);
                host.overlayHost.removeView(shade);
                openDialog();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(46));
            params.setMargins(0, host.dp(2), 0, host.dp(2));
            panel.addView(choice, params);
        }
        shade.addView(panel, host.centerParams(host.dp(330), -2));
        host.overlayHost.addView(shade);
    }

    private void applyPreset(String presetId) {
        SharedPreferences.Editor editor = prefs().edit().putString(ACTIVE_PRESET, presetId);
        if (!PRESET_CUSTOM.equals(presetId) && PRESET_CUSTOM.equals(activePreset())) {
            for (int band = 0; band < BAND_COUNT; band++) {
                editor.putInt(CUSTOM_BAND_PREFIX + band, bandLevel(band));
            }
        }
        if (PRESET_CUSTOM.equals(presetId)) {
            for (int band = 0; band < BAND_COUNT; band++) {
                editor.putInt(BAND_PREFIX + band,
                        prefs().getInt(CUSTOM_BAND_PREFIX + band, bandLevel(band)));
            }
        } else {
            int presetIndex = presetIndex(presetId);
            for (int band = 0; band < BAND_COUNT; band++) {
                editor.putInt(BAND_PREFIX + band, PRESET_LEVELS[presetIndex][band]);
            }
        }
        editor.apply();
        dispatchSettings();
    }

    private String activePreset() {
        return prefs().getString(ACTIVE_PRESET, PRESET_CUSTOM);
    }

    private int presetIndex(String presetId) {
        for (int index = 0; index < PRESET_LEVELS.length; index++) {
            if (PRESET_IDS[index].equals(presetId)) {
                return index;
            }
        }
        return 0;
    }

    private String presetName(String presetId) {
        if ("flat".equals(presetId)) return host.tr("Flat", "Ровный");
        if ("bass".equals(presetId)) return host.tr("Bass", "Бас");
        if ("vocal".equals(presetId)) return host.tr("Vocal", "Вокал");
        if ("rock".equals(presetId)) return host.tr("Rock", "Рок");
        if ("electronic".equals(presetId)) return host.tr("Electronic", "Электроника");
        if ("classical".equals(presetId)) return host.tr("Classical", "Классика");
        return host.tr("Custom", "Своя");
    }

    private int bandLevel(int band) {
        return Math.max(MIN_DB, Math.min(MAX_DB, prefs().getInt(BAND_PREFIX + band, 0)));
    }

    private boolean enabled() {
        return prefs().getBoolean(ENABLED, false);
    }

    private void setEnabled(boolean value) {
        prefs().edit().putBoolean(ENABLED, value).apply();
        dispatchSettings();
        refreshButton();
    }

    private void refreshButton() {
        if (this.playerButton == null) {
            return;
        }
        if (enabled()) {
            host.uiFactory.applyPlayerToolStyle(this.playerButton, true);
        } else {
            host.uiFactory.applyPlayerToolStyle(this.playerButton, false);
        }
    }

    private void styleToggle(Button button) {
        if (enabled()) {
            host.uiFactory.applyPrimaryButtonStyle(button);
        } else {
            host.uiFactory.applySecondaryButtonStyle(button);
        }
    }

    private SharedPreferences prefs() {
        return host.getSharedPreferences(PREFS, 0);
    }

    private void dispatchSettings() {
        host.refreshPlaybackAppearance();
    }
}
