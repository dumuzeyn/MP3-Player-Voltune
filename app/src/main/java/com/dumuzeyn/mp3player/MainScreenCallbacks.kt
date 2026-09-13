package com.dumuzeyn.mp3player

import android.widget.FrameLayout
import android.widget.LinearLayout

/** Connects top-level screen construction to focused UI controllers. */
internal class MainScreenCallbacks(
    private val tabs: TabsController,
    private val playerUi: PlayerUiController,
    private val songs: SongsRenderer,
    private val particles: ValueProvider<ParticleEffectsView>,
    private val animations: BooleanValueProvider,
    private val darkTheme: BooleanValueProvider,
    private val baseColor: IntValueProvider,
    private val visibleArtworkPromotion: Runnable,
) : MainScreenView.Callbacks, PlayerGradientBackground.Config {
    override fun buildTabs(page: LinearLayout) {
        tabs.buildTabs(page)
    }

    override fun buildMiniPlayer(root: FrameLayout) {
        playerUi.buildMini(root)
    }

    override fun onContentScrolled() {
        songs.loadMoreIfNearBottom()
        visibleArtworkPromotion.run()
    }

    override fun createParticles(): ParticleEffectsView = particles.get()

    override fun gradientConfig(): PlayerGradientBackground.Config = this

    override fun animationsEnabled(): Boolean = animations.get()

    override fun darkTheme(): Boolean = darkTheme.get()

    override fun baseColor(): Int = baseColor.get()
}
