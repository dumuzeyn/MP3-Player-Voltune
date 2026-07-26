package com.dumuzeyn.mp3player;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.dumuzeyn.mp3player.playback.service.PlaybackSleepTimer;

final class SleepTimerController {
    private final MainActivityCore host;

    SleepTimerController(MainActivityCore host) {
        this.host = host;
    }

    void openDialog() {
        syncFromService();
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.text(host.tr3("Sleep timer", "Таймер сна", "◷"), 22, true), new LinearLayout.LayoutParams(-1, host.dp(46)));

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.VERTICAL);
        int[] values = {5, 15, 30, host.appearanceState.customTimerMinutes};
        String[] labels = {"5 min", "15 min", "30 min", host.appearanceState.customTimerMinutes + " min"};
        for (int i = 0; i < values.length; i++) {
            final int minutes = values[i];
            Button button = host.uiFactory.button(labels[i]);
            button.setOnClickListener(view -> {
                host.overlayHost.removeView(shade);
                start(minutes);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(50));
            params.setMargins(0, host.dp(4), 0, host.dp(4));
            actions.addView(button, params);
        }

        Button custom = host.uiFactory.button(host.tr3("Custom time", "Свое время", "Custom"));
        custom.setOnClickListener(view -> {
            host.overlayHost.removeView(shade);
            openCustomDialog();
        });
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(-1, host.dp(50));
        customParams.setMargins(0, host.dp(4), 0, host.dp(4));
        actions.addView(custom, customParams);

        if (host.playbackUiState.sleepTimerEndsAt > 0) {
            Button cancel = host.uiFactory.button(host.tr3("Disable timer", "Выключить таймер", "×"));
            cancel.setOnClickListener(view -> {
                host.overlayHost.removeView(shade);
                cancel();
            });
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, host.dp(50));
            cancelParams.setMargins(0, host.dp(4), 0, 0);
            actions.addView(cancel, cancelParams);
        }

        panel.addView(actions);
        shade.addView(panel, host.centerParams(host.dp(330), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    void openCustomDialog() {
        host.overlayController.showInput(host.tr3("Custom time", "Свое время", "◷"),
                host.tr3("Minutes", "Минуты", "′"),
                String.valueOf(host.appearanceState.customTimerMinutes),
                true,
                value -> {
                    try {
                        host.appearanceState.customTimerMinutes = Math.max(1, Integer.parseInt(value.trim()));
                        host.saveState();
                        start(host.appearanceState.customTimerMinutes);
                    } catch (Exception ignored) {
                    }
                });
    }

    void start(int minutes) {
        long delayMs = Math.max(1L, (long) minutes) * 60L * 1000L;
        host.playbackUiState.sleepTimerEndsAt = System.currentTimeMillis() + delayMs;
        host.playbackController.startSleepTimer(delayMs);
        host.playerUiController.syncPlaybackUi();
    }

    void cancel() {
        host.playbackUiState.sleepTimerEndsAt = 0L;
        host.playbackController.cancelSleepTimer();
        host.playerUiController.syncPlaybackUi();
    }

    String buttonText() {
        if (host.playbackUiState.sleepTimerEndsAt > 0L
                && host.playbackUiState.sleepTimerEndsAt <= System.currentTimeMillis()) {
            syncFromService();
        }
        if (host.playbackUiState.sleepTimerEndsAt <= 0) {
            return host.tr("Timer ◷", "Таймер ◷");
        }
        long remainingMs = Math.max(0L, host.playbackUiState.sleepTimerEndsAt - System.currentTimeMillis());
        return host.formatSeconds((remainingMs + 999L) / 1000L);
    }

    private void syncFromService() {
        host.playbackUiState.sleepTimerEndsAt = PlaybackSleepTimer.readEndsAt(host);
    }
}
