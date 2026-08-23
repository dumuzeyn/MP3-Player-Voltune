package com.dumuzeyn.mp3player;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

final class CardTransparencyController {
    private static final int MIN_OPACITY = 35;
    private static final int MAX_OPACITY = 100;
    private final MainActivityCore host;

    CardTransparencyController(MainActivityCore host) {
        this.host = host;
    }

    String settingLabel() {
        return host.tr("Card opacity by section", "Прозрачность карточек по разделам");
    }

    void openDialog() {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12));
        panel.addView(host.uiFactory.dialogTitle(settingLabel(), 18),
                host.uiFactory.dialogTitleParams());

        LinearLayout controls = new LinearLayout(host);
        controls.setOrientation(LinearLayout.VERTICAL);
        addControl(controls, host.tr("Songs", "Песни"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.songCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.songCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Favorites", "Избранное"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.favoriteCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.favoriteCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Playlists", "Плейлисты"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.playlistCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.playlistCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Genres", "Жанры"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.genreCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.genreCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Artists", "Исполнители"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.artistCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.artistCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Albums", "Альбомы"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.albumCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.albumCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Settings", "Настройки"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.settingsCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.settingsCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Mini-player", "Мини-плеер"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.miniPlayerCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.miniPlayerCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Application header", "Шапка приложения"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.headerCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.headerCardOpacity = value;
                    }
                });
        addControl(controls, host.tr("Dialogs", "Диалоговые окна"),
                new OpacityValue() {
                    @Override
                    public int get() {
                        return host.appearanceState.dialogCardOpacity;
                    }

                    @Override
                    public void set(int value) {
                        host.appearanceState.dialogCardOpacity = value;
                    }
                });

        ScrollView scroll = new ScrollView(host);
        scroll.addView(controls, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            host.saveState();
            host.overlayHost.removeView(shade);
            host.rebuildUi();
        });
        panel.addView(done, new LinearLayout.LayoutParams(-1, host.dp(48)));
        int maxHeight = host.getResources().getDisplayMetrics().heightPixels - host.dp(96);
        shade.addView(panel, host.centerParams(host.dp(350), Math.min(host.dp(600), maxHeight)));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private void addControl(LinearLayout panel, String title, OpacityValue value) {
        TextView label = host.uiFactory.text(labelText(title, value.get()), 14, true);
        panel.addView(label, new LinearLayout.LayoutParams(-1, host.dp(24)));

        SeekBar seek = new SeekBar(host);
        seek.setMax(MAX_OPACITY - MIN_OPACITY);
        seek.setProgress(value.get() - MIN_OPACITY);
        host.uiFactory.applySeekBarColors(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    value.set(MIN_OPACITY + progress);
                    label.setText(labelText(title, value.get()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                host.saveState();
            }
        });
        panel.addView(seek, new LinearLayout.LayoutParams(-1, host.dp(32)));
    }

    private String labelText(String title, int value) {
        return title + ": " + value + "%";
    }

    private interface OpacityValue {
        int get();

        void set(int value);
    }
}
