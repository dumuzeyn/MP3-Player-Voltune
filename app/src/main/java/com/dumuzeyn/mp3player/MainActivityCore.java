package com.dumuzeyn.mp3player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.dumuzeyn.mp3player.library.SongDiagnostics;
import com.dumuzeyn.mp3player.ui.player.PlaybackTimeFormatter;
import com.dumuzeyn.mp3player.ui.layout.ResponsiveLayoutController;
import java.util.ArrayList;

class MainActivityCore extends Activity {
    static final int COVER_FULL_SIZE = 1024;
    static final int TAB_CYCLES = 5;
    int bg;
    int fg;
    int line;
    LinearLayout list;
    Button miniButton;
    LinearLayout miniPlayer;
    TextView miniSub;
    TextView miniTitle;
    int muted;
    int purple;
    int purpleDark;
    int purpleSoft;
    int yellow;
    int yellowDark;
    int yellowSoft;
    int card;
    int cardStroke;
    int primaryText;
    int secondaryText;
    FrameLayout overlayHost;
    LinearLayout page;
    int panel;
    FrameLayout root;
    LinearLayout tabRow;
    String[] tabs;
    HorizontalScrollView tabsScroll;
    FrameLayout contentHost;
    ScrollView contentScroll;
    final LibraryState libraryState = new LibraryState();
    final NavigationState navigationState = new NavigationState();
    final AppearanceState appearanceState = new AppearanceState();
    final PlaybackUiState playbackUiState = new PlaybackUiState();
    private ParticleEffectsView particleEffectsView;
    final Handler uiHandler = new Handler(Looper.getMainLooper());
    final Handler playbackHandler = new Handler(Looper.getMainLooper());
    final SongRowStateRegistry songRows = new SongRowStateRegistry();
    final SongRowStateRegistry previewSongRows = new SongRowStateRegistry();
    final TrackArtworkUi artworkUi = new TrackArtworkUi(this, this.uiHandler,
            new TrackArtworkDependencies(
                    () -> navigationState.renderingTabPreview,
                    this::activeSongRows, this::findTrack,
                    this::isCurrent, this::isPlaybackPlaying,
                    () -> purple, () -> yellow, () -> purpleSoft));
    final SongsRenderer songsRenderer = new SongsRenderer(this);
    private final SettingsRenderer settingsRenderer = new SettingsRenderer(this);
    final SettingsController settingsController = new SettingsController(this);
    final TabsController tabsController = new TabsController(this);
    private final SwipeController swipeController = new SwipeController(this);
    final AudioImportController audioImportController = new AudioImportController(this);
    final UiFactory uiFactory = new UiFactory(this);
    private final HeaderController headerController = new HeaderController(this);
    final OverlayController overlayController = new OverlayController(this);
    private final DialogController dialogController = new DialogController(this);
    final BackNavigationController backNavigationController = new BackNavigationController(this);
    final ThemeController themeController = new ThemeController(this);
    final PlaybackController playbackController = new PlaybackController(this);
    final PlaybackQueueController playbackQueueController =
            new PlaybackQueueController(this, playbackController);
    final PlaybackActions playbackActions =
            new Media3PlaybackActions(playbackQueueController, playbackController);
    final PlaybackStateProvider playbackStateProvider =
            new Media3PlaybackStateProvider(libraryState, playbackUiState);
    final PlayerUiController playerUiController =
            new PlayerUiController(this, playbackActions, playbackStateProvider);
    final SleepTimerController sleepTimerController = new SleepTimerController(this);
    final EqualizerController equalizerController = new EqualizerController(this);
    final VolumeLevelingController volumeLevelingController = new VolumeLevelingController(this);
    final ParticleSettingsController particleSettingsController = new ParticleSettingsController(this);
    final CoverRotationSettingsController coverRotationSettingsController =
            new CoverRotationSettingsController(this);
    final UninterruptedPlaybackController uninterruptedPlaybackController = new UninterruptedPlaybackController(this);
    final StableVolumeController stableVolumeController = new StableVolumeController(this);
    final BackgroundPlaybackSettingsController backgroundPlaybackSettingsController =
            new BackgroundPlaybackSettingsController(this);
    final PlaylistTickerSettingsController playlistTickerSettingsController = new PlaylistTickerSettingsController(this);
    final CardTransparencyController cardTransparencyController = new CardTransparencyController(this);
    final BackgroundSettingsController backgroundSettingsController = new BackgroundSettingsController(this);
    final LibraryListController libraryListController = new LibraryListController(this);
    final PlaylistController playlistController = new PlaylistController(this);
    final MainRenderer mainRenderer = new MainRenderer(this);
    final UiPreferencesStore uiPreferencesStore = new UiPreferencesStore(this);
    final LibraryPersistenceController libraryPersistenceController =
            new LibraryPersistenceController(this);
    final LibraryRepository libraryRepository = new LibraryRepository(
            this.libraryState.tracks, this.libraryState.favorites,
            this.libraryState.playlists,
            this.libraryPersistenceController::save);
    final ResponsiveLayoutController responsiveLayoutController =
            new ResponsiveLayoutController(this);
    private final MainScreenView mainScreenView =
            new MainScreenView(this, this.responsiveLayoutController);
    private final MainScreenCallbacks mainScreenCallbacks = new MainScreenCallbacks(
            this.headerController, this.tabsController, this.playerUiController,
            this.songsRenderer, () -> new ParticleEffectsView(this),
            () -> this.appearanceState.animations,
            () -> this.appearanceState.dark, () -> this.bg);
    final TabTransitionCoordinator tabTransitionCoordinator =
            new TabTransitionCoordinator(this.navigationState,
                    this.mainScreenView, this.mainRenderer,
                    this.backNavigationController, this.tabsController, this.songsRenderer,
                    (scroll, content) -> {
                        this.contentScroll = scroll;
                        this.list = content;
                    });
    private final MainActivityCoordinator activityCoordinator =
            new MainActivityCoordinator(this);
    Button sourcePlayButton;

    interface InputDone {
        void done(String str);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.activityCoordinator.onCreate(bundle);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (this.particleEffectsView != null) {
            this.particleEffectsView.observeTouch(motionEvent);
        }
        if (this.swipeController.handle(motionEvent)) {
            return true;
        }
        return super.dispatchTouchEvent(motionEvent);
    }

    @Override
    protected void onStop() {
        this.activityCoordinator.onStop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.activityCoordinator.onResume();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        this.artworkUi.onTrimMemory(level);
    }

    @Override
    protected void onDestroy() {
        this.activityCoordinator.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!this.activityCoordinator.handleBack()) {
            super.onBackPressed();
        }
    }

    void restoreTabFromBack(int targetIndex, String previousSearch) {
        int direction = this.tabsController.directionTo(targetIndex);
        this.swipeController.animateToTab(targetIndex, direction, false, previousSearch);
    }

    private boolean english() {
        return "en".equals(this.appearanceState.language);
    }

    String tr(String str, String str2) {
        return english() ? str : str2;
    }

    String tr3(String str, String str2, String str3) {
        return english() ? str : str2;
    }

    String languageName() {
        return english() ? "English" : "Русский";
    }

    private void refreshTabLabels() {
        this.tabs = new String[]{tr3("Songs", "Песни", "♪"), tr3("Favorites", "Избранное", "♥"), tr3("Playlists", "Плейлисты", "▤"), tr3("Genres", "Жанры", "◇"), tr3("Artists", "Исполнители", "♙"), tr3("Albums", "Альбомы", "▣"), tr3("Settings", "Настройки", "⚙")};
        if (this.navigationState.tabIndex >= this.tabs.length) {
            this.navigationState.tabIndex = 0;
        }
    }

    void saveState() {
        this.uiPreferencesStore.save();
        saveLibraryState();
    }

    void saveUiState() {
        this.uiPreferencesStore.save();
    }

    void saveLibraryState() {
        this.libraryRepository.persistCollections();
    }

    PlaybackSnapshot playbackSnapshot() {
        return this.playbackUiState.snapshot();
    }

    void updatePlaybackSnapshot(PlaybackSnapshot snapshot) {
        this.playbackUiState.updateSnapshot(snapshot);
    }

    int currentTrackIndex() {
        return this.playbackUiState.currentTrackIndex(this.libraryState);
    }

    boolean isPlaybackPlaying() {
        return this.playbackUiState.isPlaying();
    }

    int repeatMode() {
        return this.playbackUiState.repeatMode();
    }

    boolean isShuffleEnabled() {
        return this.playbackUiState.shuffleEnabled();
    }

    private void colors() {
        this.themeController.applyPalette();
    }

    void refreshAfterTrackChange() {
        refreshPlaybackChrome();
    }

    private void refreshPlaybackChrome() {
        this.songRows.refresh(songRowStateResolver());
        this.playlistController.refreshPlaybackState();
        if (this.sourcePlayButton != null) {
            this.sourcePlayButton.setText(this.playbackQueueController.isPlayingSource(
                    currentVisibleTracks()) ? "Ⅱ" : "▶");
        }
        this.playerUiController.updateMini();
    }

    SongRowStateRegistry.StateResolver songRowStateResolver() {
        return new SongRowStateRegistry.StateResolver() {
            @Override
            public Track findTrack(String uri) {
                return MainActivityCore.this.findTrack(uri);
            }

            @Override
            public boolean isCurrent(Track track) {
                return MainActivityCore.this.isCurrent(track);
            }

            @Override
            public boolean isPlaying() {
                return MainActivityCore.this.isPlaybackPlaying();
            }

            @Override
            public int activeColor() {
                return MainActivityCore.this.purple;
            }

            @Override
            public int secondaryActiveColor() {
                return MainActivityCore.this.yellow;
            }

            @Override
            public int inactiveColor() {
                return MainActivityCore.this.purpleSoft;
            }
        };
    }

    void buildUi() {
        colors();
        this.themeController.applyWindow();
        refreshTabLabels();
        MainScreenView.Appearance appearance = new MainScreenView.Appearance(
                this.appearanceState.mainSolidBackground == 0 ? this.bg : this.appearanceState.mainSolidBackground,
                this.appearanceState.mainBackgroundMode, this.appearanceState.mainGradientStart, this.appearanceState.mainGradientEnd,
                this.appearanceState.mainBackgroundMediaUri, this.appearanceState.mainBackgroundBlur);
        MainScreenView.References views = this.mainScreenView.build(
                appearance, this.mainScreenCallbacks);
        this.root = views.root;
        this.page = views.page;
        this.contentHost = views.contentHost;
        this.contentScroll = views.contentScroll;
        this.list = views.contentList;
        this.overlayHost = views.overlayHost;
        this.particleEffectsView = views.particles;
        setContentView(this.root);
        render();
    }

    void rebuildUiForTheme() {
        this.mainRenderer.captureScrollBeforeUiRebuild();
        buildUi();
    }

    void rebuildUi() {
        this.mainRenderer.captureScrollBeforeUiRebuild();
        buildUi();
    }

    void refreshTabs() {
        this.tabsController.refreshTabs();
    }

    void switchTabAnimated(int i, int i2) {
        if (this.tabs == null || i == this.navigationState.tabIndex || this.navigationState.tabAnimating) {
            return;
        }
        this.navigationState.preferredTabDirection = i2;
        this.swipeController.animateToTab(i, i2, true, "");
    }

    void scrollTabsToActive(boolean z) {
        this.tabsController.scrollToActive(z, this.navigationState.tabIndex);
    }

    void scrollTabsToActive(boolean z, int i) {
        this.tabsController.scrollToActive(z, i);
    }

    void render() {
        this.mainRenderer.render();
    }

    MainRenderer.PreviewState renderTabPreview(
            LinearLayout target, int targetIndex, String targetSearch) {
        return this.mainRenderer.renderPreview(target, targetIndex, targetSearch);
    }

    void discardTabPreview() {
        this.mainRenderer.discardPreview();
    }

    SongRowStateRegistry activeSongRows() {
        return this.navigationState.renderingTabPreview ? this.previewSongRows : this.songRows;
    }

    void renderSectionHeader() {
        this.headerController.renderSectionHeader();
    }

    void renderSettings() {
        this.settingsRenderer.render();
    }

    void refreshSettingsLabels() {
        this.settingsRenderer.refreshDynamicLabels();
    }

    String themeName() {
        return this.themeController.themeName();
    }

    void openThemeDialog() {
        this.themeController.openDialog();
    }

    void stopPlaybackAndClearQueue() {
        this.playbackQueueController.clear();
        this.playerUiController.syncPlaybackUi();
        refreshAfterTrackChange();
    }

    ArrayList<Track> currentVisibleTracks() {
        return this.libraryListController.currentVisibleTracks();
    }

    boolean matchesTrackSearch(Track track, String query) {
        return this.libraryListController.matchesTrackSearch(track, query);
    }

    boolean containsSearch(String value, String query) {
        return this.libraryListController.containsSearch(value, query);
    }

    void renderSongs(ArrayList<Track> arrayList) {
        this.songsRenderer.render(arrayList);
    }

    String loopLabel() {
        return this.playbackQueueController.loopLabel();
    }

    String formatMs(int i) {
        return PlaybackTimeFormatter.formatMilliseconds(i);
    }

    String formatTrackDuration(Track track) {
        return track.durationMs > 0 ? formatMs(track.durationMs) : "--:--";
    }

    int playbackDurationFor(Track track) {
        int serviceDuration = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, this.playbackController.duration()));
        if (serviceDuration > 0) {
            return serviceDuration;
        }
        return track == null ? 0 : Math.max(0, track.durationMs);
    }

    String formatSeconds(long j) {
        return PlaybackTimeFormatter.formatSeconds(j);
    }

    void refreshParticleSettings() {
        if (this.particleEffectsView != null) {
            this.particleEffectsView.settingsChanged();
        }
    }

    void refreshPlaybackAppearance() {
        this.playbackController.refreshAudioEffects();
    }

    String timerButtonText() {
        return this.sleepTimerController.buttonText();
    }

    int playbackPosition() {
        return (int) Math.min(Integer.MAX_VALUE, this.playbackController.currentPosition());
    }

    void toggleFavorite(Track track) {
        this.libraryRepository.toggleFavorite(track);
    }

    boolean isCurrent(Track track) {
        int currentIndex = currentTrackIndex();
        return currentIndex >= 0 && currentIndex < this.libraryState.tracks.size()
                && this.libraryState.tracks.get(currentIndex).uri.equals(track.uri);
    }

    Track findTrack(String str) {
        return this.libraryRepository.find(str);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    void reloadUiPreferences() {
        this.uiPreferencesStore.load();
        rebuildUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (this.settingsController.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        if (this.backgroundPlaybackSettingsController.handleActivityResult(requestCode)) {
            return;
        }
        if (this.backgroundSettingsController.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        this.audioImportController.handleActivityResult(requestCode, resultCode, data);
    }

    int cardSurfaceColor(int color) {
        return cardSurfaceColor(color, this.appearanceState.cardOpacity);
    }

    int cardSurfaceColor(int color, int opacity) {
        return Color.argb(Math.round(255.0f * opacity / 100.0f),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    void addMiniSpacerIfNeeded() {
        int currentIndex = currentTrackIndex();
        if (currentIndex < 0 || currentIndex >= this.libraryState.tracks.size()
                || this.overlayHost.getChildCount() > 0) {
            return;
        }
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(88)));
        this.list.addView(view);
    }

    void openSongDiagnostics() {
        SongDiagnostics.Result result = SongDiagnostics.inspect(this, this.libraryState.tracks);
        String message = tr("Available: ", "Доступно: ") + result.available
                + "\n" + tr("Unavailable: ", "Недоступно: ") + result.unavailable
                + "\n" + tr("With duration: ", "С длительностью: ") + result.withDuration
                + "\n" + tr("Without duration: ", "Без длительности: ") + result.withoutDuration
                + (result.problemTitles.isEmpty()
                        ? ""
                        : "\n" + tr("Problem tracks:", "Проблемные треки:") + result.problemTitles);
        showConfirmPanel(tr("Song check", "Проверка песен"), message, new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    void showConfirmPanel(String title, String message, Runnable yesAction) {
        this.dialogController.showConfirmation(title, message, yesAction);
    }

    void showActionPanel(String title, String message, String negativeLabel,
            String positiveLabel, Runnable action) {
        this.dialogController.showConfirmation(
                title, message, negativeLabel, positiveLabel, action);
    }

    void showActionPanel(String title, String message, String negativeLabel,
            String positiveLabel, boolean emphasizePositive, Runnable action) {
        this.dialogController.showConfirmation(
                title, message, negativeLabel, positiveLabel, emphasizePositive, action);
    }

    FrameLayout.LayoutParams centerParams(int i, int i2) {
        return this.responsiveLayoutController.centeredPanelParams(i, i2);
    }

    FrameLayout.LayoutParams bottomParams() {
        return this.responsiveLayoutController.bottomPanelParams();
    }

    int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

}
