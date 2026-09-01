package com.dumuzeyn.mp3player;

import android.widget.FrameLayout;

/** Builds the main view tree and refreshes playback-dependent chrome. */
final class MainActivityViewController {
    private final MainActivityCore host;
    private final MainScreenView screen;
    private final MainScreenCallbacks callbacks;

    MainActivityViewController(MainActivityCore host, MainScreenView screen,
            MainScreenCallbacks callbacks) {
        this.host = host;
        this.screen = screen;
        this.callbacks = callbacks;
    }

    void build() {
        host.themeController.applyPalette();
        host.themeController.applyWindow();
        host.refreshTabLabels();
        MainScreenView.Appearance appearance = new MainScreenView.Appearance(
                host.appearanceState.mainSolidBackground == 0
                        ? host.bg : host.appearanceState.mainSolidBackground,
                host.appearanceState.mainBackgroundMode,
                host.appearanceState.mainGradientStart,
                host.appearanceState.mainGradientEnd,
                host.appearanceState.mainBackgroundMediaUri,
                host.appearanceState.mainBackgroundBlur);
        MainScreenView.References views = screen.build(appearance, callbacks);
        host.root = views.root;
        host.page = views.page;
        host.contentHost = views.contentHost;
        host.contentScroll = views.contentScroll;
        host.list = views.contentList;
        if (host.songsView != null) {
            host.songsView.close();
        }
        host.songsView = new SongsView(host);
        host.contentHost.addView(host.songsView, new FrameLayout.LayoutParams(-1, -1));
        host.overlayHost = views.overlayHost;
        host.setParticleEffectsView(views.particles);
        host.setContentView(host.root);
        host.render();
    }

    void refreshPlaybackChrome() {
        host.songRows.refresh(stateResolver());
        host.mainRenderer.refreshHomePlayback();
        if (host.songsView != null) {
            host.songsView.refreshPlayback();
        }
        host.playlistController.refreshPlaybackState();
        host.overlayController.refreshPlayback();
        if (host.sourcePlayButton != null) {
            host.sourcePlayButton.setText(host.playbackQueueController.isPlayingSource(
                    host.currentVisibleTracks()) ? "Ⅱ" : "▶");
        }
        host.playerUiController.updateMini();
    }

    SongRowStateRegistry.StateResolver stateResolver() {
        return new SongRowStateRegistry.StateResolver() {
            @Override
            public Track findTrack(String uri) {
                return host.findTrack(uri);
            }

            @Override
            public boolean isCurrent(Track track) {
                return host.isCurrent(track);
            }

            @Override
            public boolean isPlaying() {
                return host.isPlaybackPlaying();
            }

            @Override
            public int activeColor() {
                return host.purple;
            }

            @Override
            public int secondaryActiveColor() {
                return host.yellow;
            }

            @Override
            public int inactiveColor() {
                return host.purpleSoft;
            }
        };
    }
}
