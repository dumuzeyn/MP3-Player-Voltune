package com.dumuzeyn.mp3player;

import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Set;

final class VolumeLevelingController {
    static final String ENABLED = "volume_leveling_enabled";
    private final MainActivityCore host;
    private Button playerButton;
    private TrackLoudnessNormalizer analyzer;

    VolumeLevelingController(MainActivityCore host) {
        this.host = host;
    }

    String settingLabel() {
        return host.tr("Volume leveling: ", "Единая громкость: ")
                + host.tr(enabled() ? "on" : "off", enabled() ? "вкл" : "выкл");
    }

    Button createPlayerButton() {
        Button button = host.uiFactory.button(buttonText());
        button.setSingleLine(true);
        button.setTextSize(13.0f);
        button.setOnClickListener(view -> toggle());
        playerButton = button;
        refreshButton();
        return button;
    }

    void openDialog() {
        TrackLoudnessNormalizer normalizer = analyzer();
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14));
        panel.addView(host.uiFactory.text(host.tr("Volume leveling", "Единая громкость"), 22, true),
                new LinearLayout.LayoutParams(-1, host.dp(44)));

        Button enabledButton = dialogButton(settingLabel());
        enabledButton.setOnClickListener(view -> {
            toggle();
            enabledButton.setText(settingLabel());
        });
        panel.addView(enabledButton, rowParams());

        Button reduceOnly = dialogButton(reduceOnlyLabel());
        reduceOnly.setOnClickListener(view -> {
            prefs().edit().putBoolean(TrackLoudnessNormalizer.REDUCE_ONLY,
                    !reduceOnly()).apply();
            reduceOnly.setText(reduceOnlyLabel());
            dispatchSettings();
        });
        panel.addView(reduceOnly, rowParams());

        TextView targetLabel = host.uiFactory.text(targetLabel(), 14, false);
        panel.addView(targetLabel, new LinearLayout.LayoutParams(-1, host.dp(28)));
        SeekBar target = new SeekBar(host);
        target.setMax(14);
        target.setProgress(targetLufs() + 24);
        host.uiFactory.applySeekBarColors(target);
        target.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                prefs().edit().putInt(TrackLoudnessNormalizer.TARGET_LUFS,
                        progress - 24).apply();
                targetLabel.setText(targetLabel());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                dispatchSettings();
            }
        });
        panel.addView(target, new LinearLayout.LayoutParams(-1, host.dp(36)));

        TextView status = host.uiFactory.text(statusText(normalizer), 14, false);
        panel.addView(status, new LinearLayout.LayoutParams(-1, host.dp(50)));

        Button analyze = dialogButton(host.tr("Analyze library", "Анализировать медиатеку"));
        analyze.setOnClickListener(view -> normalizer.analyzeLibrary(host.libraryState.tracks,
                progressListener(normalizer, status)));
        panel.addView(analyze, rowParams());

        Button cancel = dialogButton(host.tr("Cancel analysis", "Отменить анализ"));
        cancel.setOnClickListener(view -> {
            normalizer.cancelAnalysis();
            status.setText(host.tr("Cancelling analysis...", "Анализ отменяется..."));
        });
        panel.addView(cancel, rowParams());

        Button retry = dialogButton(host.tr("Retry errors", "Повторить ошибки"));
        retry.setOnClickListener(view -> {
            Set<String> failed = normalizer.failedTrackIds();
            ArrayList<Track> retryTracks = new ArrayList<>();
            for (Track track : host.libraryState.tracks) {
                if (failed.contains(track.trackId)) {
                    retryTracks.add(track);
                }
            }
            normalizer.analyzeLibrary(retryTracks, progressListener(normalizer, status));
        });
        panel.addView(retry, rowParams());

        Button clear = dialogButton(host.tr("Clear analysis cache", "Очистить результаты анализа"));
        clear.setOnClickListener(view -> {
            normalizer.clearCache();
            status.setText(statusText(normalizer));
            dispatchSettings();
        });
        panel.addView(clear, rowParams());

        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> host.overlayHost.removeView(shade));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, host.dp(46));
        doneParams.setMargins(0, host.dp(6), 0, 0);
        panel.addView(done, doneParams);

        shade.addView(panel, host.centerParams(host.dp(350), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    void release() {
        if (analyzer != null) {
            analyzer.release();
            analyzer = null;
        }
    }

    private TrackLoudnessNormalizer.ProgressListener progressListener(
            TrackLoudnessNormalizer normalizer, TextView status) {
        return (completed, total, errors, finished, cancelled) -> {
            if (finished) {
                status.setText(statusText(normalizer) + (cancelled
                        ? host.tr(" · cancelled", " · отменено") : ""));
                dispatchSettings();
            } else {
                status.setText(host.tr("Analysis: ", "Анализ: ") + completed + "/" + total
                        + host.tr(" · errors: ", " · ошибок: ") + errors);
            }
        };
    }

    private Button dialogButton(String text) {
        Button button = host.uiFactory.button(text);
        button.setTextSize(15.0f);
        host.uiFactory.applySecondaryButtonStyle(button);
        return button;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(44));
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        return params;
    }

    private String statusText(TrackLoudnessNormalizer normalizer) {
        return host.tr("Analyzed: ", "Проанализировано: ")
                + normalizer.analyzedCount(host.libraryState.tracks)
                + "/" + host.libraryState.tracks.size()
                + host.tr(" · file errors: ", " · ошибок файлов: ")
                + normalizer.errorCount(host.libraryState.tracks);
    }

    private String reduceOnlyLabel() {
        return host.tr("Advanced mode, reduce only: ", "Расширенный режим, только уменьшение: ")
                + host.tr(reduceOnly() ? "on" : "off", reduceOnly() ? "вкл" : "выкл");
    }

    private String targetLabel() {
        return host.tr("Target level: ", "Целевой уровень: ") + targetLufs() + " LUFS";
    }

    private int targetLufs() {
        return Math.max(-24, Math.min(-10, prefs().getInt(
                TrackLoudnessNormalizer.TARGET_LUFS,
                Math.round(LoudnessGainPolicy.DEFAULT_TARGET_LUFS))));
    }

    private void toggle() {
        prefs().edit().putBoolean(ENABLED, !enabled()).apply();
        refreshButton();
        dispatchSettings();
    }

    private boolean enabled() {
        return prefs().getBoolean(ENABLED, false);
    }

    private boolean reduceOnly() {
        return prefs().getBoolean(TrackLoudnessNormalizer.REDUCE_ONLY, false);
    }

    private String buttonText() {
        return host.tr(enabled() ? "Level ●" : "Level ○",
                enabled() ? "Уровень ●" : "Уровень ○");
    }

    private void refreshButton() {
        if (playerButton == null) {
            return;
        }
        playerButton.setText(buttonText());
        host.uiFactory.applyPlayerToolStyle(playerButton, enabled());
    }

    private SharedPreferences prefs() {
        return host.getSharedPreferences(EqualizerController.PREFS, 0);
    }

    private TrackLoudnessNormalizer analyzer() {
        if (analyzer == null) {
            analyzer = new TrackLoudnessNormalizer(host);
        }
        return analyzer;
    }

    private void dispatchSettings() {
        host.refreshPlaybackAppearance();
    }
}
