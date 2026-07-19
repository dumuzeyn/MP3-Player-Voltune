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

    void buildAppHeader() {
        FrameLayout header = new FrameLayout(host);
        host.applyCardStyle(header, host.headerCardOpacity);
        header.setPadding(host.dp(12), 0, host.dp(12), 0);
        LinearLayout row = host.row();
        ImageView icon = new ImageView(host);
        icon.setImageBitmap(AppIconRenderer.renderLogo(
                host, host.purple, host.yellow, host.dp(42)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setContentDescription("MP3 Player Voltune");
        LinearLayout.LayoutParams iconParams = host.square(36);
        iconParams.setMargins(0, 0, host.dp(8), 0);
        row.addView(icon, iconParams);
        TextView title = host.text("MP3 Player Voltune", 20, true);
        title.setTextColor(host.primaryText);
        row.addView(title, new LinearLayout.LayoutParams(0, host.dp(52), 1.0f));
        TriangleDecorView artwork = new TriangleDecorView(host);
        artwork.setMode(TriangleDecorView.HEADER);
        artwork.setColors(host.purple, host.yellow);
        artwork.setDecorAlpha(host.dark ? 0.78f : 0.9f);
        artwork.setStrokeWidth(host.dp(2));
        row.addView(artwork, new LinearLayout.LayoutParams(host.dp(68), host.dp(46)));
        header.addView(row, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(60));
        params.setMargins(0, 0, 0, host.dp(8));
        host.page.addView(header, params);
    }

    void renderSectionHeader() {
        LinearLayout section = new LinearLayout(host);
        section.setOrientation(LinearLayout.VERTICAL);
        String title = host.tabs[host.tabIndex];
        if (host.tabIndex == 0) {
            title = host.tr("Songs ", "Песни ") + host.tracks.size();
        }
        TextView titleView = host.text(title, 22, true);
        titleView.setSingleLine(true);
        section.addView(titleView, new LinearLayout.LayoutParams(-1, host.dp(48)));
        if (host.tabIndex == 0 || host.tabIndex == 1) {
            section.addView(libraryActions(), new LinearLayout.LayoutParams(-1, host.dp(62)));
        } else if (host.tabIndex == 2) {
            LinearLayout actions = host.row();
            actions.addView(actionButton("+", view -> host.createPlaylistDialog()), host.square(52));
            actions.addView(actionButton("⌕", view -> host.openSearch()), host.square(52));
            section.addView(actions, new LinearLayout.LayoutParams(-1, host.dp(62)));
        }
        host.list.addView(section);
    }

    private LinearLayout libraryActions() {
        LinearLayout actions = host.row();
        if (host.tabIndex == 0) {
            actions.addView(actionButton("+", view -> host.openPicker()), host.square(52));
            actions.addView(actionButton("▣", view -> host.openFolderPicker()), host.square(52));
        } else {
            actions.addView(actionButton("+", view -> host.openAddFavorites()), host.square(52));
        }
        actions.addView(actionButton("⌕", view -> host.openSearch()), host.square(52));
        final ArrayList<Track> visible = host.currentVisibleTracks();
        Button play = actionButton(host.isPlayingSource(visible) ? "Ⅱ" : "▶", view -> {
            if (host.isPlayingSource(visible)) {
                host.toggleCurrent();
            } else {
                host.playList(visible, false);
            }
        });
        host.sourcePlayButton = play;
        actions.addView(play, host.square(52));
        Button shuffle = host.shuffleButton();
        shuffle.setOnClickListener(view -> host.playList(host.currentVisibleTracks(), true));
        actions.addView(shuffle, host.square(52));
        return actions;
    }

    private Button actionButton(String symbol, android.view.View.OnClickListener listener) {
        Button button = host.icon(symbol);
        button.setOnClickListener(listener);
        return button;
    }
}
