package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.List;

/** Searchable multi-track chooser used by playlists, favorites, and the queue. */
final class TrackSelectionOverlayController {
    private final MainActivityCore host;

    TrackSelectionOverlayController(MainActivityCore host) {
        this.host = host;
    }

    void open(String title, HashSet<String> selected, OverlayController.SelectionDone done) {
        FrameLayout shade = host.uiFactory.shade();
        String owner = "selection-" + Integer.toHexString(System.identityHashCode(shade));
        cancelSearchOnDetach(shade, owner);
        LinearLayout panel = host.uiFactory.panelCard();
        panel.addView(header(title, shade, owner, selected, done));
        EditText search = searchField();
        panel.addView(search, searchParams());
        ScrollView scroll = new ScrollView(host);
        LinearLayout rows = new LinearLayout(host);
        rows.setOrientation(LinearLayout.VERTICAL);
        renderRows(rows, selected, host.libraryState.tracks);
        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                host.trackSearchController.filter(owner, host.libraryState.tracks,
                        value == null ? "" : value.toString(), filtered -> {
                            if (shade.getParent() != null) {
                                renderRows(rows, selected, filtered);
                            }
                        });
            }
        });
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        shade.addView(panel, host.bottomParams());
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private void cancelSearchOnDetach(FrameLayout shade, String owner) {
        shade.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                host.trackSearchController.cancel(owner);
            }
        });
    }

    private LinearLayout header(String title, FrameLayout shade, String owner,
            HashSet<String> selected, OverlayController.SelectionDone done) {
        LinearLayout header = host.uiFactory.row();
        header.addView(host.uiFactory.text(title, 20, true),
                new LinearLayout.LayoutParams(0, host.dp(58), 1.0f));
        Button complete = host.uiFactory.icon("✔");
        complete.setOnClickListener(view -> {
            host.trackSearchController.cancel(owner);
            host.overlayHost.removeView(shade);
            done.done(selected);
            host.playerUiController.updateMini();
        });
        header.addView(complete, host.uiFactory.square(52));
        Button close = host.uiFactory.icon("×");
        close.setOnClickListener(view -> close(shade, owner));
        header.addView(close, host.uiFactory.square(52));
        return header;
    }

    private void renderRows(LinearLayout parent, HashSet<String> selected,
            List<Track> tracks) {
        parent.removeAllViews();
        for (Track track : tracks) {
            LinearLayout row = host.uiFactory.row();
            row.setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(8));
            ImageView cover = host.uiFactory.coverView();
            int fallback = fallbackColor();
            host.artworkUi.loadUnregisteredCover(cover, track, fallback,
                    CoverLoader.THUMB_SIZE);
            row.addView(cover, host.uiFactory.square(58));
            TextView title = host.uiFactory.text(track.title, 17, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(host.dp(12), 0, host.dp(8), 0);
            row.addView(title, new LinearLayout.LayoutParams(0, host.dp(70), 1.0f));
            Button mark = host.uiFactory.icon("");
            row.addView(mark, host.uiFactory.square(48));
            Button play = host.uiFactory.icon(host.isCurrent(track)
                    && host.isPlaybackPlaying() ? "Ⅱ" : "▶");
            play.setOnClickListener(view -> {
                if (host.isCurrent(track)) {
                    host.playbackQueueController.toggleOrStart();
                } else {
                    host.playbackQueueController.playTrack(track, false);
                }
                renderRows(parent, selected, tracks);
            });
            row.addView(play, host.uiFactory.square(48));
            Runnable refresh = () -> applyAppearance(row, cover, title, mark, play,
                    selected.contains(track.uri));
            mark.setOnClickListener(view -> {
                if (!selected.add(track.uri)) {
                    selected.remove(track.uri);
                }
                refresh.run();
            });
            refresh.run();
            parent.addView(host.uiFactory.spaced(row));
        }
    }

    private void applyAppearance(LinearLayout row, ImageView cover, TextView title,
            Button mark, Button play, boolean selected) {
        int selectedSurface = host.appearanceState.dark ? host.purpleDark : host.purpleSoft;
        int selectedContent = ThemeManager.readableOn(selectedSurface);
        host.uiFactory.setSurface(row, selected ? selectedSurface : host.panel, false);
        cover.setBackgroundColor(selected ? selectedSurface : fallbackColor());
        title.setTextColor(selected ? selectedContent : host.fg);
        mark.setText(selected ? "✔" : "+");
        host.uiFactory.applyPlainIconStyle(mark, selected ? selectedContent : host.purple);
        host.uiFactory.applyPlainIconStyle(play, selected ? selectedContent : host.purple);
    }

    private EditText searchField() {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setHint(host.tr("Search songs", "Поиск песен"));
        input.setTextColor(host.fg);
        input.setHintTextColor(host.muted);
        input.setTextSize(16.0f);
        input.setPadding(host.dp(14), 0, host.dp(14), 0);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        host.uiFactory.setSurface(input, host.panel, true);
        return input;
    }

    private LinearLayout.LayoutParams searchParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(58));
        params.setMargins(0, host.dp(8), 0, host.dp(12));
        return params;
    }

    private int fallbackColor() {
        int channel = host.appearanceState.dark ? 28 : 235;
        return Color.rgb(channel, channel, channel);
    }

    private void close(FrameLayout shade, String owner) {
        host.trackSearchController.cancel(owner);
        if (shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        host.playerUiController.updateMini();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void afterTextChanged(Editable s) { }
    }
}
