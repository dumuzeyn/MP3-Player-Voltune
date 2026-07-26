package com.dumuzeyn.mp3player;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

final class HeaderController {
    private final MainActivityCore host;

    HeaderController(MainActivityCore host) {
        this.host = host;
    }

    void buildAppHeader(LinearLayout page) {
        FrameLayout header = new FrameLayout(host);
        host.uiFactory.applyCardStyle(header, host.appearanceState.headerCardOpacity);
        header.setPadding(host.dp(12), 0, host.dp(12), 0);
        LinearLayout row = host.uiFactory.row();
        ImageView icon = new ImageView(host);
        icon.setImageBitmap(AppIconRenderer.renderLogo(
                host, host.purple, host.yellow, host.dp(42)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setContentDescription("MP3 Player Voltune");
        LinearLayout.LayoutParams iconParams = host.uiFactory.square(36);
        iconParams.setMargins(0, 0, host.dp(8), 0);
        row.addView(icon, iconParams);
        TextView title = host.uiFactory.text("MP3 Player Voltune", 20, true);
        title.setTextColor(host.primaryText);
        row.addView(title, new LinearLayout.LayoutParams(0, host.dp(52), 1.0f));
        TriangleDecorView artwork = new TriangleDecorView(host);
        artwork.setMode(TriangleDecorView.HEADER);
        artwork.setColors(host.purple, host.yellow);
        artwork.setDecorAlpha(host.appearanceState.dark ? 0.78f : 0.9f);
        artwork.setStrokeWidth(host.dp(2));
        row.addView(artwork, new LinearLayout.LayoutParams(host.dp(68), host.dp(46)));
        header.addView(row, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(60));
        params.setMargins(0, 0, 0, host.dp(8));
        page.addView(header, params);
    }

    void renderSectionHeader() {
        LinearLayout section = new LinearLayout(host);
        section.setOrientation(LinearLayout.VERTICAL);
        String title = host.tabs[host.navigationState.tabIndex];
        if (host.navigationState.tabIndex == 0) {
            title = host.tr("Songs ", "Песни ") + host.libraryState.tracks.size();
        }
        TextView titleView = host.uiFactory.text(title, 22, true);
        titleView.setSingleLine(true);
        section.addView(titleView, new LinearLayout.LayoutParams(-1, host.dp(48)));
        if (host.navigationState.tabIndex == 0 || host.navigationState.tabIndex == 1) {
            section.addView(libraryActions(), new LinearLayout.LayoutParams(-1, host.dp(62)));
        } else if (host.navigationState.tabIndex == 2) {
            LinearLayout actions = host.uiFactory.row();
            actions.addView(actionButton("+", view -> host.overlayController.createPlaylist()), host.uiFactory.square(52));
            actions.addView(actionButton("⌕", view -> host.overlayController.openSearch()), host.uiFactory.square(52));
            section.addView(actions, new LinearLayout.LayoutParams(-1, host.dp(62)));
        }
        host.list.addView(section);
    }

    private LinearLayout libraryActions() {
        LinearLayout actions = host.uiFactory.row();
        if (host.navigationState.tabIndex == 0) {
            actions.addView(actionButton("+", view -> host.audioImportController.openFiles()), host.uiFactory.square(52));
            actions.addView(actionButton("▣", view -> host.audioImportController.openFolder()), host.uiFactory.square(52));
        } else {
            actions.addView(actionButton("+", view -> host.overlayController.openAddFavorites()), host.uiFactory.square(52));
        }
        actions.addView(actionButton("⌕", view -> host.overlayController.openSearch()), host.uiFactory.square(52));
        final ArrayList<Track> visible = host.currentVisibleTracks();
        Button play = actionButton(host.playbackQueueController.isPlayingSource(visible) ? "Ⅱ" : "▶", view -> {
            if (host.playbackQueueController.isPlayingSource(visible)) {
                host.playbackQueueController.toggleOrStart();
            } else {
                host.playbackQueueController.playList(visible, false);
            }
        });
        host.sourcePlayButton = play;
        actions.addView(play, host.uiFactory.square(52));
        Button shuffle = host.uiFactory.shuffleButton();
        shuffle.setOnClickListener(view -> host.playbackQueueController.playList(host.currentVisibleTracks(), true));
        actions.addView(shuffle, host.uiFactory.square(52));
        return actions;
    }

    private Button actionButton(String symbol, android.view.View.OnClickListener listener) {
        Button button = host.uiFactory.icon(symbol);
        button.setOnClickListener(listener);
        return button;
    }
}
