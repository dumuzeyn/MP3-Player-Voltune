package com.dumuzeyn.mp3player;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
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
        icon.setContentDescription(host.getString(R.string.app_name));
        LinearLayout.LayoutParams iconParams = host.uiFactory.square(36);
        iconParams.setMargins(0, 0, host.dp(8), 0);
        row.addView(icon, iconParams);
        SpannableString brand = new SpannableString(host.getString(R.string.app_name));
        brand.setSpan(new ForegroundColorSpan(host.purple), 0, 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView title = host.uiFactory.text(brand.toString(), 22, true);
        title.setText(brand);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(host.primaryText);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);
        title.setLetterSpacing(0.0f);
        row.addView(title, new LinearLayout.LayoutParams(0, host.dp(52), 1.0f));
        header.addView(row, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(60));
        params.setMargins(0, 0, 0, host.dp(8));
        page.addView(header, params);
    }

    void renderSectionHeader() {
        host.list.addView(createSectionHeader());
    }

    View createSectionHeader() {
        return createSectionHeader(host.navigationState.tabIndex);
    }

    View createSongsSectionHeader() {
        return createSectionHeader(LibraryTabs.SONGS);
    }

    private View createSectionHeader(int tabIndex) {
        LinearLayout section = new LinearLayout(host);
        section.setOrientation(LinearLayout.VERTICAL);
        if (tabIndex == LibraryTabs.SONGS || tabIndex == LibraryTabs.FAVORITES) {
            section.addView(libraryActions(tabIndex),
                    new LinearLayout.LayoutParams(-1, host.dp(62)));
        } else if (tabIndex == LibraryTabs.PLAYLISTS) {
            LinearLayout actions = host.uiFactory.row();
            actions.addView(actionButton("+", view -> host.overlayController.createPlaylist()), host.uiFactory.square(52));
            actions.addView(actionButton("⌕", view -> host.overlayController.openSearch()), host.uiFactory.square(52));
            section.addView(actions, new LinearLayout.LayoutParams(-1, host.dp(62)));
        }
        return section;
    }

    void refreshSongsSectionHeader(View section) {
        refreshSectionHeader(section, 0);
    }

    private void refreshSectionHeader(View section, int tabIndex) {
        if (section == null) {
            return;
        }
        Button play = section.findViewById(R.id.section_play);
        if (play != null) {
            play.setText(host.playbackQueueController.isPlayingSource(
                    host.currentVisibleTracks()) ? "Ⅱ" : "▶");
            host.sourcePlayButton = play;
        }
    }

    private LinearLayout libraryActions(int tabIndex) {
        LinearLayout actions = host.uiFactory.row();
        if (tabIndex == LibraryTabs.SONGS) {
            actions.addView(actionButton("+", view -> host.audioImportController.openFiles()), host.uiFactory.square(52));
            actions.addView(actionButton("▣", view -> host.audioImportController.openFolder()), host.uiFactory.square(52));
        } else {
            actions.addView(actionButton("+", view -> host.overlayController.openAddFavorites()), host.uiFactory.square(52));
        }
        actions.addView(actionButton("⌕", view -> host.overlayController.openSearch()), host.uiFactory.square(52));
        ArrayList<Track> visible = host.currentVisibleTracks();
        Button play = actionButton(host.playbackQueueController.isPlayingSource(visible) ? "Ⅱ" : "▶", view -> {
            ArrayList<Track> currentVisible = host.currentVisibleTracks();
            if (host.playbackQueueController.isPlayingSource(currentVisible)) {
                host.playbackQueueController.toggleOrStart();
            } else {
                host.playbackQueueController.playList(currentVisible, false);
            }
        });
        play.setId(R.id.section_play);
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
