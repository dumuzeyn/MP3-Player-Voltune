package com.dumuzeyn.mp3player;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.app.Activity;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class SettingsController {
    private static final int EXPORT_BACKUP = 5201;
    private static final int IMPORT_BACKUP = 5202;
    private static final int EXPORT_THEME = 5203;
    private static final int IMPORT_THEME = 5204;
    private static final int EXPORT_PLAYBACK_DIAGNOSTICS = 5205;
    private static final String SUPPORT_URL = "https://pay.cloudtips.ru/p/54e5a4f9";
    private final MainActivityCore host;

    SettingsController(MainActivityCore host) {
        this.host = host;
    }

    String resumeWindowText() {
        if (host.appearanceState.resumeWindowMinutes <= 0) {
            return host.tr("off", "выкл");
        }
        if (host.appearanceState.resumeWindowMinutes % 60 == 0) {
            int hours = host.appearanceState.resumeWindowMinutes / 60;
            return hours + " " + host.tr(hours == 1 ? "hour" : "hours", "ч");
        }
        return host.appearanceState.resumeWindowMinutes + " " + host.tr("min", "мин");
    }

    void openLanguageDialog() {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.text(host.tr("Language", "Язык"), 22, true),
                new LinearLayout.LayoutParams(-1, host.dp(50)));
        addChoice(panel, "English", "en".equals(host.appearanceState.language), () -> applyLanguage("en", shade));
        addChoice(panel, "Русский", "ru".equals(host.appearanceState.language), () -> applyLanguage("ru", shade));
        addDoneButton(panel, shade);
        shade.addView(panel, host.centerParams(host.dp(330), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    void openResumeWindowDialog() {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.text(host.tr("Mini-player memory", "Память мини-плеера"), 22, true),
                new LinearLayout.LayoutParams(-1, host.dp(50)));
        int[] values = {30, 60, 120, 240, 480, 0};
        for (final int value : values) {
            String label = value == 0
                    ? host.tr("Off", "Отключено")
                    : value % 60 == 0
                            ? (value / 60) + " " + host.tr(value == 60 ? "hour" : "hours", "ч")
                            : value + " " + host.tr("minutes", "мин");
            addChoice(panel, label, host.appearanceState.resumeWindowMinutes == value, () -> {
                host.appearanceState.resumeWindowMinutes = value;
                host.saveState();
                host.overlayHost.removeView(shade);
                host.refreshSettingsLabels();
                openResumeWindowDialog();
            });
        }
        addDoneButton(panel, shade);
        shade.addView(panel, host.centerParams(host.dp(330), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    void openGithub() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/dumuzeyn/MP3-Player-Voltune"));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            host.startActivity(intent);
        } catch (RuntimeException error) {
            host.showConfirmPanel(
                    host.tr("GitHub is unavailable", "GitHub недоступен"),
                    host.tr("No application can open the project link.",
                            "Нет приложения, которое может открыть ссылку проекта."),
                    () -> {
                    });
        }
    }

    void openAuthorSupport() {
        host.showActionPanel(
                host.tr("Support the author", "Поддержка автора"),
                host.tr(
                        "You will be taken to an external CloudTips page.\n\n"
                                + "Support is voluntary and non-refundable. It does not unlock "
                                + "additional features, a subscription, or other benefits. "
                                + "All application features remain free.",
                        "Вы перейдёте на внешнюю страницу CloudTips.\n\n"
                                + "Поддержка является добровольной и безвозмездной. Она не открывает "
                                + "дополнительные функции, подписку или другие преимущества. "
                                + "Все возможности приложения остаются бесплатными."),
                host.tr("Cancel", "Отмена"),
                host.tr("Support", "Поддержать"),
                false,
                this::openSupportPage);
    }

    private void openSupportPage() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            host.startActivity(intent);
        } catch (RuntimeException error) {
            host.showConfirmPanel(
                    host.tr("CloudTips is unavailable", "CloudTips недоступен"),
                    host.tr("No application can open the support page.",
                            "Нет приложения, которое может открыть страницу поддержки."),
                    () -> {
                    });
        }
    }

    void openCrashReports() {
        int count = CrashReportStore.count(host);
        if (count == 0) {
            host.showConfirmPanel(
                    host.tr("Crash reports", "Отчёты о сбоях"),
                    host.tr("No local crash reports have been recorded.",
                            "Локальных отчётов о сбоях нет."),
                    () -> {
                    });
            return;
        }
        String latest = CrashReportStore.latestSummary(host);
        String message = host.tr("Saved reports: ", "Сохранено отчётов: ") + count
                + "\n" + host.tr("Latest: ", "Последний: ") + latest
                + "\n\n" + host.tr(
                        "Reports stay only on this device and do not contain music URIs. Clear them?",
                        "Отчёты хранятся только на устройстве и не содержат URI музыки. Очистить их?");
        host.showConfirmPanel(
                host.tr("Crash reports", "Отчёты о сбоях"),
                message,
                () -> {
                    CrashReportStore.clear(host);
                    host.render();
                });
    }

    void confirmDeleteAllSongs() {
        host.showConfirmPanel(
                host.tr("Delete all songs?", "Удалить все песни?"),
                host.tr("Songs will disappear only from this app. Files on the phone will stay untouched.",
                        "Песни исчезнут только из приложения. Файлы на телефоне останутся."),
                () -> {
                    host.stopPlaybackAndClearQueue();
                    host.libraryState.tracks.clear();
                    host.libraryState.favorites.clear();
                    for (Playlist playlist : host.libraryState.playlists) {
                        playlist.uris.clear();
                    }
                    TrackStore.save(host, host.libraryState.tracks);
                    host.saveState();
                    host.render();
                });
    }

    void exportLibraryBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "Voltune-backup.json");
        host.startActivityForResult(intent, EXPORT_BACKUP);
    }

    void importLibraryBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        host.startActivityForResult(intent, IMPORT_BACKUP);
    }

    void exportTheme() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "Voltune-theme.json");
        host.startActivityForResult(intent, EXPORT_THEME);
    }

    void importTheme() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        host.startActivityForResult(intent, IMPORT_THEME);
    }

    void confirmExportPlaybackDiagnostics() {
        host.showActionPanel(
                host.tr("Export playback diagnostics", "Экспорт диагностики воспроизведения"),
                host.tr(
                        "The report contains the app and Android versions, device model, "
                                + "current playback state and up to 200 recent playback events. "
                                + "Music paths, URIs, file names and tags are not included. "
                                + "The file is created only after you confirm.",
                        "Отчёт содержит версии приложения и Android, модель устройства, "
                                + "текущее состояние и до 200 последних событий воспроизведения. "
                                + "Пути, URI, имена файлов и теги музыки не добавляются. "
                                + "Файл будет создан только после вашего подтверждения."),
                host.tr("Cancel", "Отмена"),
                host.tr("Export", "Экспортировать"),
                false,
                this::exportPlaybackDiagnostics);
    }

    private void exportPlaybackDiagnostics() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "Voltune-playback-diagnostics.txt");
        host.startActivityForResult(intent, EXPORT_PLAYBACK_DIAGNOSTICS);
    }

    boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != EXPORT_BACKUP && requestCode != IMPORT_BACKUP
                && requestCode != EXPORT_THEME && requestCode != IMPORT_THEME
                && requestCode != EXPORT_PLAYBACK_DIAGNOSTICS) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        try {
            if (requestCode == EXPORT_BACKUP || requestCode == EXPORT_THEME
                    || requestCode == EXPORT_PLAYBACK_DIAGNOSTICS) {
                String backup;
                if (requestCode == EXPORT_BACKUP) {
                    backup = LibraryBackupManager.exportBackup(host, host.libraryState.tracks, host.libraryState.playlists);
                } else if (requestCode == EXPORT_THEME) {
                    backup = ThemePresetCodec.encode(
                            host.getSharedPreferences("mp3_player_ui", 0));
                } else {
                    backup = PlaybackEventLogger.buildReport(host);
                }
                OutputStream output = host.getContentResolver().openOutputStream(data.getData(),
                        "wt");
                if (output == null) {
                    throw new IllegalStateException("Output file is unavailable");
                }
                try {
                    output.write(backup.getBytes(StandardCharsets.UTF_8));
                } finally {
                    output.close();
                }
                Toast.makeText(host, host.tr("File exported", "Файл сохранён"),
                        Toast.LENGTH_SHORT).show();
            } else {
                InputStream input = host.getContentResolver().openInputStream(data.getData());
                if (input == null) {
                    throw new IllegalStateException("Input file is unavailable");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        total += count;
                        int maxBytes = requestCode == IMPORT_BACKUP
                                ? LibraryBackupManager.MAX_BACKUP_BYTES : ThemePresetCodec.MAX_BYTES;
                        if (total > maxBytes) {
                            throw new IllegalArgumentException("Backup is too large");
                        }
                        bytes.write(buffer, 0, count);
                    }
                } finally {
                    input.close();
                }
                String encoded = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                if (requestCode == IMPORT_BACKUP) {
                    LibraryBackupManager.ImportResult imported =
                            LibraryBackupManager.importBackup(host, encoded, host.libraryState.tracks);
                    host.libraryState.playlists.clear();
                    host.libraryState.playlists.addAll(imported.playlists);
                    host.saveState();
                    host.rebuildUi();
                } else {
                    ThemePresetCodec.decodeInto(encoded,
                            host.getSharedPreferences("mp3_player_ui", 0));
                    host.reloadUiPreferences();
                }
                Toast.makeText(host, host.tr("File restored", "Файл восстановлен"),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception error) {
            host.showConfirmPanel(host.tr("Backup error", "Ошибка резервной копии"),
                    host.tr("The selected file is damaged or unsupported.",
                            "Выбранный файл повреждён или не поддерживается."), () -> {
                    });
        }
        return true;
    }

    void confirmRemoveUnavailableSongs() {
        LibraryFileAccessManager.Result result = LibraryFileAccessManager.inspect(host,
                host.libraryState.tracks);
        if (result.unavailable.isEmpty()) {
            host.showConfirmPanel(
                    host.tr("File access", "Доступ к файлам"),
                    host.tr("All library files are available.",
                            "Все файлы медиатеки доступны."),
                    () -> {
                    });
            return;
        }
        host.showConfirmPanel(
                host.tr("Remove unavailable songs?", "Удалить недоступные песни?"),
                host.tr("Unavailable records: ", "Недоступных записей: ")
                        + result.unavailable.size() + "\n\n"
                        + host.tr("The audio files themselves will not be deleted.",
                                "Сами аудиофайлы удалены не будут."),
                () -> {
                    LibraryFileAccessManager.removeUnavailable(host, host.libraryState.tracks,
                            host.libraryState.favorites, host.libraryState.playlists);
                    host.saveState();
                    host.render();
                });
    }

    void confirmDeleteAllPlaylists() {
        host.showConfirmPanel(
                host.tr("Delete all playlists?", "Удалить все плейлисты?"),
                host.tr("Songs will stay in the app.", "Песни останутся в приложении."),
                () -> {
                    host.libraryState.playlists.clear();
                    host.saveState();
                    host.render();
                });
    }

    private void applyLanguage(String language, FrameLayout shade) {
        host.appearanceState.language = language;
        host.saveState();
        host.overlayHost.removeView(shade);
        host.rebuildUi();
        openLanguageDialog();
    }

    private void addDoneButton(LinearLayout parent, FrameLayout shade) {
        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        host.uiFactory.applyPrimaryButtonStyle(done);
        done.setOnClickListener(view -> {
            if (shade.getParent() != null) {
                host.overlayHost.removeView(shade);
            }
            host.playerUiController.updateMini();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(50));
        params.setMargins(0, host.dp(8), 0, 0);
        parent.addView(done, params);
    }

    private void addChoice(LinearLayout parent, String label, boolean selected, Runnable action) {
        Button button = host.uiFactory.button(label);
        button.setTextSize(17.0f);
        button.setGravity(8388627);
        button.setPadding(host.dp(18), 0, host.dp(12), 0);
        if (selected) {
            host.uiFactory.applyPrimaryButtonStyle(button);
        } else {
            host.uiFactory.applySecondaryButtonStyle(button);
        }
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(54));
        params.setMargins(0, host.dp(5), 0, host.dp(5));
        parent.addView(button, params);
    }
}
