package com.dumuzeyn.mp3player;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Controls the vinyl-style cover rotation speed used by the full player. */
final class CoverRotationSettingsController {
    private static final int MIN_SPEED = 25;
    private static final int MAX_SPEED = 200;

    private final MainActivityCore host;

    CoverRotationSettingsController(MainActivityCore host) {
        this.host = host;
    }

    String settingLabel() {
        return host.tr("Full-player disc speed: ", "Скорость диска в плеере: ")
                + host.appearanceState.fullPlayerRotationSpeed + "%";
    }

    void openDialog() {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));

        TextView label = host.uiFactory.text(settingLabel(), 17, true);
        panel.addView(label, new LinearLayout.LayoutParams(-1, host.dp(52)));

        SeekBar seek = new SeekBar(host);
        seek.setMax(MAX_SPEED - MIN_SPEED);
        seek.setProgress(host.appearanceState.fullPlayerRotationSpeed - MIN_SPEED);
        host.uiFactory.applySeekBarColors(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                host.appearanceState.fullPlayerRotationSpeed = MIN_SPEED + progress;
                label.setText(settingLabel());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                host.saveState();
            }
        });
        panel.addView(seek, new LinearLayout.LayoutParams(-1, host.dp(48)));

        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            host.saveState();
            host.overlayHost.removeView(shade);
            host.refreshSettingsLabels();
        });
        panel.addView(done, new LinearLayout.LayoutParams(-1, host.dp(50)));

        shade.addView(panel, host.centerParams(host.dp(340), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }
}
