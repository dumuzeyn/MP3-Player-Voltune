package com.dumuzeyn.mp3player;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.dumuzeyn.mp3player.ui.layout.ResponsiveLayoutController;

/** Builds and owns the stable top-level view tree of the main screen. */
final class MainScreenView {
    interface Callbacks {
        void buildHeader(LinearLayout page);

        void buildTabs(LinearLayout page);

        void buildMiniPlayer(FrameLayout root);

        void onContentScrolled();

        ParticleEffectsView createParticles();

        PlayerGradientBackground.Config gradientConfig();
    }

    static final class Appearance {
        final int solidColor;
        final int backgroundMode;
        final int gradientStart;
        final int gradientEnd;
        final String mediaUri;
        final int mediaBlur;

        Appearance(int solidColor, int backgroundMode, int gradientStart, int gradientEnd,
                String mediaUri, int mediaBlur) {
            this.solidColor = solidColor;
            this.backgroundMode = backgroundMode;
            this.gradientStart = gradientStart;
            this.gradientEnd = gradientEnd;
            this.mediaUri = mediaUri == null ? "" : mediaUri;
            this.mediaBlur = mediaBlur;
        }
    }

    static final class References {
        final FrameLayout root;
        final LinearLayout page;
        final FrameLayout contentHost;
        final ScrollView contentScroll;
        final LinearLayout contentList;
        final FrameLayout overlayHost;
        final ParticleEffectsView particles;

        References(FrameLayout root, LinearLayout page, FrameLayout contentHost,
                ScrollView contentScroll, LinearLayout contentList,
                FrameLayout overlayHost, ParticleEffectsView particles) {
            this.root = root;
            this.page = page;
            this.contentHost = contentHost;
            this.contentScroll = contentScroll;
            this.contentList = contentList;
            this.overlayHost = overlayHost;
            this.particles = particles;
        }
    }

    private final Context context;
    private final ResponsiveLayoutController layout;
    private References references;

    MainScreenView(Context context, ResponsiveLayoutController layout) {
        this.context = context;
        this.layout = layout;
    }

    References build(Appearance appearance, Callbacks callbacks) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(appearance.solidColor);
        addBackground(root, appearance, callbacks);

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = layout.pageHorizontalPadding();
        page.setPadding(horizontalPadding, layout.pageTopPadding(), horizontalPadding, 0);
        root.addView(page, layout.mainPageParams());
        callbacks.buildHeader(page);
        callbacks.buildTabs(page);

        FrameLayout contentHost = new FrameLayout(context);
        ScrollView contentScroll = new ScrollView(context);
        LinearLayout contentList = new LinearLayout(context);
        contentList.setOrientation(LinearLayout.VERTICAL);
        contentScroll.addView(contentList, new FrameLayout.LayoutParams(-1, -2));
        contentScroll.setOnScrollChangeListener(
                (view, scrollX, scrollY, oldScrollX, oldScrollY) ->
                        callbacks.onContentScrolled());
        contentHost.addView(contentScroll, new FrameLayout.LayoutParams(-1, -1));
        page.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        FrameLayout overlayHost = new FrameLayout(context);
        root.addView(overlayHost, new FrameLayout.LayoutParams(-1, -1));
        callbacks.buildMiniPlayer(root);

        ParticleEffectsView particles = callbacks.createParticles();
        root.addView(particles, new FrameLayout.LayoutParams(-1, -1));
        references = new References(root, page, contentHost, contentScroll, contentList,
                overlayHost, particles);
        return references;
    }

    FrameLayout contentHost() {
        return references == null ? null : references.contentHost;
    }

    ScrollView contentScroll() {
        return references == null ? null : references.contentScroll;
    }

    void replaceContent(ScrollView scrollView, LinearLayout content) {
        if (references == null) {
            return;
        }
        references = new References(references.root, references.page,
                references.contentHost, scrollView, content,
                references.overlayHost, references.particles);
    }

    private void addBackground(
            FrameLayout root, Appearance appearance, Callbacks callbacks) {
        if (appearance.backgroundMode == BackgroundSettingsController.MODE_GRADIENT) {
            root.addView(new PlayerGradientBackground(
                    context, callbacks.gradientConfig(),
                    appearance.gradientStart, appearance.gradientEnd),
                    new FrameLayout.LayoutParams(-1, -1));
        } else if (appearance.backgroundMode == BackgroundSettingsController.MODE_MEDIA
                && !appearance.mediaUri.isEmpty()) {
            root.addView(new BackgroundMediaView(
                    context, appearance.mediaUri, appearance.mediaBlur,
                    appearance.solidColor),
                    new FrameLayout.LayoutParams(-1, -1));
        }
    }
}
