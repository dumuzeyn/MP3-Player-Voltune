package com.dumuzeyn.mp3player

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.dumuzeyn.mp3player.ui.layout.ResponsiveLayoutController

/** Builds and owns the stable top-level view tree of the main screen. */
internal class MainScreenView(
    private val context: Context,
    private val layout: ResponsiveLayoutController,
) {
    interface Callbacks {
        fun buildTabs(page: LinearLayout)
        fun buildMiniPlayer(root: FrameLayout)
        fun onContentScrolled()
        fun createParticles(): ParticleEffectsView
        fun gradientConfig(): PlayerGradientBackground.Config
    }

    class Appearance(
        val solidColor: Int,
        val backgroundMode: Int,
        val gradientStart: Int,
        val gradientEnd: Int,
        mediaUri: String?,
        val mediaBlur: Int,
    ) {
        val mediaUri = mediaUri.orEmpty()
    }

    class References(
        val root: FrameLayout,
        val page: LinearLayout,
        val contentHost: FrameLayout,
        val contentScroll: ScrollView,
        val contentList: LinearLayout,
        val overlayHost: FrameLayout,
        val particles: ParticleEffectsView,
    )

    private var references: References? = null

    fun build(appearance: Appearance, callbacks: Callbacks): References {
        val root = FrameLayout(context).apply {
            setBackgroundColor(appearance.solidColor)
        }
        addBackground(root, appearance, callbacks)

        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = layout.pageHorizontalPadding()
            setPadding(horizontalPadding, layout.pageTopPadding(), horizontalPadding, 0)
        }
        root.addView(page, layout.mainPageParams())
        callbacks.buildTabs(page)

        val contentHost = FrameLayout(context)
        val contentScroll = ScrollView(context)
        val contentList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        contentScroll.addView(contentList, FrameLayout.LayoutParams(-1, -2))
        contentScroll.setOnScrollChangeListener { _, _, _, _, _ -> callbacks.onContentScrolled() }
        contentHost.addView(contentScroll, FrameLayout.LayoutParams(-1, -1))
        page.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))

        val overlayHost = FrameLayout(context)
        root.addView(overlayHost, FrameLayout.LayoutParams(-1, -1))
        callbacks.buildMiniPlayer(root)

        val particles = callbacks.createParticles()
        root.addView(particles, FrameLayout.LayoutParams(-1, -1))
        return References(
            root,
            page,
            contentHost,
            contentScroll,
            contentList,
            overlayHost,
            particles,
        ).also { references = it }
    }

    fun contentHost(): FrameLayout? = references?.contentHost

    fun contentScroll(): ScrollView? = references?.contentScroll

    fun replaceContent(scrollView: ScrollView, content: LinearLayout) {
        val current = references ?: return
        references = References(
            current.root,
            current.page,
            current.contentHost,
            scrollView,
            content,
            current.overlayHost,
            current.particles,
        )
    }

    private fun addBackground(root: FrameLayout, appearance: Appearance, callbacks: Callbacks) {
        when {
            appearance.backgroundMode == BackgroundSettingsController.MODE_GRADIENT -> {
                root.addView(
                    PlayerGradientBackground(
                        context,
                        callbacks.gradientConfig(),
                        appearance.gradientStart,
                        appearance.gradientEnd,
                    ),
                    FrameLayout.LayoutParams(-1, -1),
                )
            }

            appearance.backgroundMode == BackgroundSettingsController.MODE_MEDIA &&
                appearance.mediaUri.isNotEmpty() -> {
                root.addView(
                    BackgroundMediaView(
                        context,
                        appearance.mediaUri,
                        appearance.mediaBlur,
                        appearance.solidColor,
                    ),
                    FrameLayout.LayoutParams(-1, -1),
                )
            }
        }
    }
}
