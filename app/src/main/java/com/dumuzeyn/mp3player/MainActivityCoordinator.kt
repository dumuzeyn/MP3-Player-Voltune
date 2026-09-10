package com.dumuzeyn.mp3player

import android.os.Bundle
import com.dumuzeyn.mp3player.playback.service.PlaybackSleepTimer
import com.dumuzeyn.mp3player.ui.permissions.DeviceAudioPermissionController
import com.dumuzeyn.mp3player.ui.permissions.NotificationPermissionController

/** Coordinates activity lifecycle without owning playback state. */
internal class MainActivityCoordinator(private val host: MainActivityCore) {
    private val closeables = CloseableRegistry().apply {
        add(AutoCloseable(host.libraryPersistenceController::close))
        add(host.libraryLoader)
        add(host.libraryMaintenanceController)
        add(host.overlayController)
        add(host.trackSearchController)
        add(host.globalSearchController)
        add(host.lyricsRepository)
        add(host.metadataEditorController)
        add(AutoCloseable { host.songsView?.close() })
        add(AutoCloseable(host.artworkUi::close))
        add(AutoCloseable(host.volumeLevelingController::release))
        add(AutoCloseable(host.playbackController::release))
        add(AutoCloseable(host.playerUiController::onHostDestroyed))
        add(AutoCloseable { host.playbackHandler.removeCallbacksAndMessages(null) })
        add(AutoCloseable { host.uiHandler.removeCallbacksAndMessages(null) })
        add(AutoCloseable(host.songsRenderer::close))
        add(AutoCloseable(host.backgroundSettingsController::close))
        add(AutoCloseable(host.audioImportController::close))
        add(host.audioEditorController)
        add(AutoCloseable(host.playbackQueueController::close))
        add(host.soundAnalysisController)
    }

    fun onCreate(savedInstanceState: Bundle?) {
        SettingsDefaults.resetForVersion243(host)
        host.uiPreferencesStore.load()
        host.playbackUiState.sleepTimerEndsAt = PlaybackSleepTimer.readEndsAt(host)
        if (!DeviceAudioPermissionController.requestIfNeeded(host)) {
            NotificationPermissionController.requestIfNeeded(host)
        }
        host.themeController.applyPalette()
        host.themeController.syncLauncherIcon()
        host.buildUi()
        host.libraryLoader.load(
            host.intent.getIntExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 0),
            host::applyLibrarySnapshot,
        )
    }

    fun onResume() {
        UiVisibilityController.apply(host, true)
        host.playbackUiState.sleepTimerEndsAt = PlaybackSleepTimer.readEndsAt(host)
        host.playbackController.enforceMiniPlayerRetention()
        host.playerUiController.syncPlaybackUi()
        host.refreshAfterTrackChange()
        host.librarySnapshotApplier.refreshHome()
    }

    fun onRequestPermissionsResult(requestCode: Int) {
        if (DeviceAudioPermissionController.handles(requestCode)) {
            host.audioImportController.onAudioPermissionChanged()
            NotificationPermissionController.requestIfNeeded(host)
        }
    }

    fun onStop() {
        UiVisibilityController.apply(host, false)
        if (!host.isChangingConfigurations) host.themeController.onHostStopped()
    }

    fun onDestroy() {
        closeables.closeAll()
    }

    fun handleBack(): Boolean = host.backNavigationController.handleBack()
}
