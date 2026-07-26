package com.dumuzeyn.mp3player;

import android.os.Bundle;
import com.dumuzeyn.mp3player.playback.service.PlaybackSleepTimer;
import com.dumuzeyn.mp3player.ui.permissions.NotificationPermissionController;

/** Coordinates activity lifecycle without owning playback state. */
final class MainActivityCoordinator {
    private final MainActivityCore host;
    private final CloseableRegistry closeables = new CloseableRegistry();

    MainActivityCoordinator(MainActivityCore host) {
        this.host = host;
        closeables.add(host.libraryPersistenceController::close);
        closeables.add(host.artworkUi::close);
        closeables.add(host.volumeLevelingController::release);
        closeables.add(host.playbackController::release);
        closeables.add(host.playerUiController::onHostDestroyed);
        closeables.add(() -> host.playbackHandler.removeCallbacksAndMessages(null));
        closeables.add(() -> host.uiHandler.removeCallbacksAndMessages(null));
        closeables.add(host.songsRenderer::close);
        closeables.add(host.backgroundSettingsController::close);
        closeables.add(host.audioImportController::close);
    }

    void onCreate(Bundle savedInstanceState) {
        SettingsDefaults.resetForVersion243(host);
        host.uiPreferencesStore.load();
        BenchmarkLibrarySeeder.seedIfRequested(host,
                host.getIntent().getIntExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 0));
        host.playbackUiState.sleepTimerEndsAt = PlaybackSleepTimer.readEndsAt(host);
        NotificationPermissionController.requestIfNeeded(host);
        host.mainRenderer.loadMenuData();
        host.playbackController.restorePersistedUiState();
        host.playbackController.connect();
        host.themeController.applyPalette();
        host.buildUi();
        host.songsRenderer.refreshMissingMetadataAsync();
        host.uiHandler.postDelayed(
                host.backgroundPlaybackSettingsController::maybePromptOnce, 900L);
    }

    void onResume() {
        host.playbackUiState.sleepTimerEndsAt = PlaybackSleepTimer.readEndsAt(host);
        host.playerUiController.syncPlaybackUi();
        host.refreshAfterTrackChange();
    }

    void onStop() {
        host.themeController.onHostStopped();
    }

    void onDestroy() {
        closeables.closeAll();
    }

    boolean handleBack() {
        return host.backNavigationController.handleBack();
    }
}
