package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

final class SettingsRenderer {
    private static final String PREFS = "mp3_player_ui";
    private static final String ADVANCED = "advancedSettingsVisible";
    private final MainActivityCore host;
    private Button memoryButton;
    private Button backgroundPlaybackButton;
    private Button volumeButton;
    private Button coverStyleButton;
    private Button rotationButton;
    private Button animationsButton;
    private Button particlesButton;
    private Button tickerButton;
    private Button soundAnalysisButton;
    private Button reanalyzeButton;
    private LinearLayout cachedContent;
    private String cachedAppearanceKey = "";

    SettingsRenderer(MainActivityCore host) {
        this.host = host;
    }

    void render() {
        boolean advanced = host.getSharedPreferences(PREFS, 0).getBoolean(ADVANCED, false);
        String appearanceKey = appearanceKey(advanced);
        if (cachedContent == null || !appearanceKey.equals(cachedAppearanceKey)) {
            rebuildCachedContent(advanced, appearanceKey);
        }
        ViewGroup parent = (ViewGroup) cachedContent.getParent();
        if (parent != null) {
            parent.removeView(cachedContent);
        }
        host.list.addView(cachedContent);
        refreshDynamicLabels();
    }

    private void rebuildCachedContent(boolean advanced, String appearanceKey) {
        LinearLayout previousTarget = host.list;
        cachedContent = new LinearLayout(host);
        cachedContent.setOrientation(LinearLayout.VERTICAL);
        host.list = cachedContent;
        try {
            renderContent(advanced);
        } finally {
            host.list = previousTarget;
        }
        cachedAppearanceKey = appearanceKey;
    }

    private void renderContent(boolean advanced) {
        section(host.tr("General", "Основные"));
        addButton(host.tr("Language: ", "Язык: ") + host.languageName(),
                view -> host.settingsController.openLanguageDialog());
        memoryButton = addButton(host.tr("Mini-player memory: ", "Память мини-плеера: ")
                        + host.settingsController.resumeWindowText(),
                view -> host.settingsController.openResumeWindowDialog());

        section(host.tr("Playback", "Воспроизведение"));
        addButton(host.uninterruptedPlaybackController.settingLabel(),
                view -> host.uninterruptedPlaybackController.toggle());
        backgroundPlaybackButton = addButton(
                host.backgroundPlaybackSettingsController.settingLabel(),
                view -> host.backgroundPlaybackSettingsController.openDialog());

        section(host.tr("Sound", "Звук"));
        volumeButton = addButton(host.volumeLevelingController.settingLabel(),
                view -> host.volumeLevelingController.openDialog());
        addButton(host.tr("Equalizer", "Эквалайзер"),
                view -> host.equalizerController.openDialog());
        addButton(host.stableVolumeController.settingLabel(),
                view -> host.stableVolumeController.toggle());

        section(host.tr("Appearance", "Внешний вид"));
        subsection(host.tr("Ready themes and accent colors", "Готовые темы и акцентные цвета"));
        addButton(host.tr("Theme: ", "Тема: ") + host.themeName(),
                view -> host.openThemeDialog());
        subsection(host.tr("Text, outline, and background", "Текст, контур и фон"));
        addButton(host.tr("Background", "Фон"),
                view -> host.backgroundSettingsController.openDialog());
        addButton(host.tr("Export theme", "Экспорт темы"),
                view -> host.settingsController.exportTheme());
        addButton(host.tr("Import theme", "Импорт темы"),
                view -> host.settingsController.importTheme());
        subsection(host.tr("Cards and artwork", "Карточки и обложки"));
        addButton(host.cardTransparencyController.settingLabel(),
                view -> host.cardTransparencyController.openDialog());
        coverStyleButton = addButton(host.tr("Cover style: ", "Стиль обложек: ")
                        + host.tr(host.appearanceState.circularCovers ? "spinning circles" : "rounded squares",
                        host.appearanceState.circularCovers ? "вращающиеся круги" : "скруглённые квадраты"),
                view -> toggleCoverStyle());
        defaults(SettingsSectionResetter.Section.APPEARANCE);

        section(host.tr("Full player", "Большой плеер"));
        rotationButton = addButton(host.coverRotationSettingsController.settingLabel(),
                view -> host.coverRotationSettingsController.openDialog());
        defaults(SettingsSectionResetter.Section.FULL_PLAYER);

        section(host.tr("Animations", "Анимации"));
        animationsButton = addButton(host.tr("Animations: ", "Анимации: ")
                        + host.tr(host.appearanceState.animations ? "on" : "off",
                        host.appearanceState.animations ? "вкл" : "выкл"), view -> toggleAnimations());
        particlesButton = addButton(host.tr("Particles: ", "Частицы: ")
                        + host.tr(host.appearanceState.particlesEnabled ? "on" : "off",
                        host.appearanceState.particlesEnabled ? "вкл" : "выкл"), view -> toggleParticles());
        addButton(host.tr("Particle settings", "Настройка частиц"),
                view -> host.particleSettingsController.openDialog());
        tickerButton = addButton(host.playlistTickerSettingsController.settingLabel(),
                view -> host.playlistTickerSettingsController.openDialog());
        defaults(SettingsSectionResetter.Section.ANIMATIONS);

        section(host.tr("Library", "Библиотека"));
        soundAnalysisButton = addButton(host.soundAnalysisController.settingLabel(),
                view -> host.soundAnalysisController.toggle());
        reanalyzeButton = addButton(reanalysisLabel(), view -> confirmFullReanalysis());
        addButton(host.tr("Check songs", "Проверить песни"),
                view -> host.openSongDiagnostics());
        addButton(host.tr("Music folders", "Музыкальные папки"),
                view -> host.settingsController.openMusicFolders());
        addButton(host.tr("Rescan music folders", "Повторно сканировать папки"),
                view -> host.audioImportController.rescanPersistedFolders());
        addButton(host.tr("Export playlists and settings", "Экспорт плейлистов и настроек"),
                view -> host.settingsController.exportLibraryBackup());
        addButton(host.tr("Import playlists and settings", "Импорт плейлистов и настроек"),
                view -> host.settingsController.importLibraryBackup());

        addPrimaryButton(host.tr(advanced ? "Hide advanced settings" : "Advanced settings",
                        advanced ? "Скрыть расширенные настройки" : "Расширенные настройки"),
                view -> {
                    host.getSharedPreferences(PREFS, 0).edit().putBoolean(ADVANCED,
                            !advanced).apply();
                    host.render();
                });
        if (advanced) {
            renderAdvanced();
        }

        section(host.tr("About", "О приложении"));
        addButton(host.tr("GitHub project", "Проект на GitHub"),
                view -> host.settingsController.openGithub());
        addButton(host.tr("Support the author", "Поддержка автора"),
                view -> host.settingsController.openAuthorSupport());
    }

    private String appearanceKey(boolean advanced) {
        return host.appearanceState.language + '|' + advanced
                + '|' + host.bg + '|' + host.fg + '|' + host.panel
                + '|' + host.cardStroke
                + '|' + host.appearanceState.settingsCardOpacity
                + '|' + host.appearanceState.textOutlineEnabled
                + '|' + host.appearanceState.textOutlineColor;
    }

    private void renderAdvanced() {
        section(host.tr("Advanced library", "Расширенная библиотека"));
        addButton(host.tr("Batch edit metadata", "Массовое изменение метаданных"),
                view -> host.metadataEditorController.openBatchSelection());
        addButton(host.tr("Remove unavailable songs", "Удалить недоступные песни"),
                view -> host.settingsController.confirmRemoveUnavailableSongs());
        addButton(host.tr("Delete all songs from app", "Удалить все песни из приложения"),
                view -> host.settingsController.confirmDeleteAllSongs());
        addButton(host.tr("Delete all playlists", "Удалить все плейлисты"),
                view -> host.settingsController.confirmDeleteAllPlaylists());

        section(host.tr("Diagnostics", "Диагностика"));
        addButton(host.tr("Crash reports: ", "Отчёты о сбоях: ")
                        + CrashReportStore.count(host),
                view -> host.settingsController.openCrashReports());
        addButton(host.tr("Export playback diagnostics", "Экспорт диагностики воспроизведения"),
                view -> host.settingsController.confirmExportPlaybackDiagnostics());
    }

    private void toggleAnimations() {
        host.appearanceState.animations = !host.appearanceState.animations;
        host.navigationState.tabAnimating = false;
        if (!host.appearanceState.animations && host.list != null) {
            host.list.animate().cancel();
            host.list.setTranslationX(0.0f);
            host.list.setAlpha(1.0f);
        }
        host.saveState();
        refreshDynamicLabels();
    }

    private void toggleParticles() {
        host.appearanceState.particlesEnabled = !host.appearanceState.particlesEnabled;
        host.saveState();
        host.refreshParticleSettings();
        refreshDynamicLabels();
    }

    private void toggleCoverStyle() {
        host.appearanceState.circularCovers = !host.appearanceState.circularCovers;
        host.saveState();
        host.refreshPlaybackAppearance();
        refreshDynamicLabels();
    }

    private void section(String label) {
        TextView title = host.uiFactory.text(label, 20, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(42));
        params.setMargins(host.dp(4), host.dp(12), host.dp(4), 0);
        host.list.addView(title, params);
    }

    private void subsection(String label) {
        TextView title = host.uiFactory.text(label, 14, true);
        title.setTextColor(host.secondaryText);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(32));
        params.setMargins(host.dp(10), host.dp(4), host.dp(10), 0);
        host.list.addView(title, params);
    }

    private void defaults(SettingsSectionResetter.Section section) {
        addButton(host.tr("Default", "По умолчанию"),
                view -> SettingsSectionResetter.reset(host, section));
    }

    void refreshDynamicLabels() {
        if (memoryButton != null) {
            memoryButton.setText(host.tr("Mini-player memory: ", "Память мини-плеера: ")
                    + host.settingsController.resumeWindowText());
        }
        if (backgroundPlaybackButton != null) {
            backgroundPlaybackButton.setText(
                    host.backgroundPlaybackSettingsController.settingLabel());
        }
        if (volumeButton != null) {
            volumeButton.setText(host.volumeLevelingController.settingLabel());
        }
        if (rotationButton != null) {
            rotationButton.setText(host.coverRotationSettingsController.settingLabel());
        }
        if (coverStyleButton != null) {
            coverStyleButton.setText(host.tr("Cover style: ", "Стиль обложек: ")
                    + host.tr(host.appearanceState.circularCovers ? "spinning circles" : "rounded squares",
                    host.appearanceState.circularCovers ? "вращающиеся круги" : "скруглённые квадраты"));
        }
        if (animationsButton != null) {
            animationsButton.setText(host.tr("Animations: ", "Анимации: ")
                    + host.tr(host.appearanceState.animations ? "on" : "off",
                    host.appearanceState.animations ? "вкл" : "выкл"));
        }
        if (particlesButton != null) {
            particlesButton.setText(host.tr("Particles: ", "Частицы: ")
                    + host.tr(host.appearanceState.particlesEnabled ? "on" : "off",
                    host.appearanceState.particlesEnabled ? "вкл" : "выкл"));
        }
        if (tickerButton != null) {
            tickerButton.setText(host.playlistTickerSettingsController.settingLabel());
        }
        if (soundAnalysisButton != null) {
            soundAnalysisButton.setText(host.soundAnalysisController.settingLabel());
        }
        if (reanalyzeButton != null) {
            reanalyzeButton.setText(reanalysisLabel());
            reanalyzeButton.setEnabled(!host.soundAnalysisController.rebuildingGroups()
                    && !host.soundAnalysisController.fullReanalysis());
        }
    }

    private String reanalysisLabel() {
        SoundAnalysisController analysis = host.soundAnalysisController;
        if (analysis.fullReanalysis()) {
            return host.tr("Re-analyzing library: ", "Повторный анализ: ")
                    + analysis.analyzed() + " / " + analysis.total();
        }
        return host.tr("Re-analyze library", "Повторно проанализировать библиотеку");
    }

    private void confirmFullReanalysis() {
        host.showConfirmPanel(
                host.tr("Re-analyze library", "Повторный анализ библиотеки"),
                host.tr("Saved sound profiles will be rebuilt for every available track. "
                                + "Playback can continue, but the operation may take time.",
                        "Сохранённые звуковые признаки будут заново построены для всех "
                                + "доступных треков. Воспроизведение продолжит работать, "
                                + "но операция может занять время."),
                () -> host.soundAnalysisController.reanalyzeLibrary());
    }

    private Button addButton(String label, View.OnClickListener listener) {
        Button button = host.uiFactory.button(label);
        button.setTextSize(17.0f);
        button.setGravity(8388627);
        button.setPadding(host.dp(18), 0, host.dp(12), 0);
        host.uiFactory.applySecondaryButtonStyle(button, host.appearanceState.settingsCardOpacity);
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.contains("delete") || lower.contains("удал")) {
            button.setTextColor(Color.rgb(190, 45, 45));
        }
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(54));
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        host.list.addView(button, params);
        return button;
    }

    private void addPrimaryButton(String label, View.OnClickListener listener) {
        Button button = host.uiFactory.button(label);
        host.uiFactory.applyPrimaryButtonStyle(button);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(50));
        params.setMargins(0, host.dp(12), 0, host.dp(4));
        host.list.addView(button, params);
    }
}
