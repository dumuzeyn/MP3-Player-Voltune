package com.dumuzeyn.mp3player;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Connects top-level screen construction to focused UI controllers. */
final class MainScreenCallbacks
        implements MainScreenView.Callbacks, PlayerGradientBackground.Config {
    private final HeaderController header;
    private final TabsController tabs;
    private final PlayerUiController playerUi;
    private final SongsRenderer songs;
    private final ValueProvider<ParticleEffectsView> particles;
    private final BooleanValueProvider animations;
    private final BooleanValueProvider darkTheme;
    private final IntValueProvider baseColor;

    MainScreenCallbacks(HeaderController header, TabsController tabs,
            PlayerUiController playerUi, SongsRenderer songs,
            ValueProvider<ParticleEffectsView> particles,
            BooleanValueProvider animations, BooleanValueProvider darkTheme,
            IntValueProvider baseColor) {
        this.header = header;
        this.tabs = tabs;
        this.playerUi = playerUi;
        this.songs = songs;
        this.particles = particles;
        this.animations = animations;
        this.darkTheme = darkTheme;
        this.baseColor = baseColor;
    }

    @Override
    public void buildHeader(LinearLayout page) {
        header.buildAppHeader(page);
    }

    @Override
    public void buildTabs(LinearLayout page) {
        tabs.buildTabs(page);
    }

    @Override
    public void buildMiniPlayer(FrameLayout root) {
        playerUi.buildMini(root);
    }

    @Override
    public void onContentScrolled() {
        songs.loadMoreIfNearBottom();
    }

    @Override
    public ParticleEffectsView createParticles() {
        return particles.get();
    }

    @Override
    public PlayerGradientBackground.Config gradientConfig() {
        return this;
    }

    @Override
    public boolean animationsEnabled() {
        return animations.get();
    }

    @Override
    public boolean darkTheme() {
        return darkTheme.get();
    }

    @Override
    public int baseColor() {
        return baseColor.get();
    }
}
