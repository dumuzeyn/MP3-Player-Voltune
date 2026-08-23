package com.dumuzeyn.mp3player;

/** Reports the built-in Media3 background behavior without requesting OS exemptions. */
final class BackgroundPlaybackSettingsController {
    private final MainActivityCore host;

    BackgroundPlaybackSettingsController(MainActivityCore host) {
        this.host = host;
    }

    String settingLabel() {
        return host.tr("Background playback: ready", "Фоновое воспроизведение: готово");
    }

    void openDialog() {
        host.showActionPanel(
                host.tr("Background playback", "Фоновое воспроизведение"),
                host.tr("Voltune keeps music, the queue, headset controls and the notification "
                                + "active while the screen is off. Visual effects pause automatically.",
                        "Voltune сохраняет музыку, очередь, управление гарнитурой и уведомление "
                                + "при выключенном экране. Визуальные эффекты останавливаются автоматически."),
                host.tr("Close", "Закрыть"), host.tr("Done", "Готово"),
                false, () -> { });
    }

    boolean handleActivityResult(int requestCode) {
        return false;
    }
}
