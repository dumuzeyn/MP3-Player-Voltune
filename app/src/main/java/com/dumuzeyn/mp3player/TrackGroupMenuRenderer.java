package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

abstract class TrackGroupMenuRenderer implements MenuRenderer {
    final MainActivityCore host;

    TrackGroupMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    abstract String groupValue(Track track);

    abstract String unknownGroupName();

    abstract int cardOpacity();

    @Override
    public boolean needsMiniSpacer() {
        return true;
    }

    @Override
    public void render() {
        String query = host.navigationState.search.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ArrayList<Track>> entry : groupedTracks().entrySet()) {
            if (!query.isEmpty()
                    && !host.containsSearch(entry.getKey(), query)
                    && !containsTrack(entry.getValue(), query)) {
                continue;
            }
            host.list.addView(host.uiFactory.spaced(groupCard(entry.getKey(), entry.getValue())));
        }
    }

    private Map<String, ArrayList<Track>> groupedTracks() {
        Map<String, ArrayList<Track>> result = new LinkedHashMap<>();
        for (Track track : host.libraryState.tracks) {
            String rawValue = groupValue(track);
            String name = rawValue == null || rawValue.trim().isEmpty()
                    ? unknownGroupName()
                    : rawValue.trim();
            ArrayList<Track> group = result.get(name);
            if (group == null) {
                group = new ArrayList<>();
                result.put(name, group);
            }
            group.add(track);
        }
        return result;
    }

    private boolean containsTrack(ArrayList<Track> tracks, String query) {
        for (Track track : tracks) {
            if (host.matchesTrackSearch(track, query)) {
                return true;
            }
        }
        return false;
    }

    private LinearLayout groupCard(final String name, final ArrayList<Track> tracks) {
        LinearLayout row = host.uiFactory.row();
        row.setPadding(host.dp(6), host.dp(4), host.dp(8), host.dp(4));
        host.uiFactory.setSurface(row, host.panel, false, cardOpacity());

        ImageView cover = host.uiFactory.coverView();
        int fallback = host.appearanceState.dark ? 28 : 235;
        if (tracks.isEmpty()) {
            cover.setBackgroundColor(Color.rgb(fallback, fallback, fallback));
        } else {
            host.artworkUi.loadCover(
                    cover, tracks.get(0), Color.rgb(fallback, fallback, fallback));
            if (cover instanceof RotatingCoverImageView) {
                ((RotatingCoverImageView) cover).bindTracks(tracks);
            }
        }
        row.addView(cover, host.uiFactory.square(52));

        LinearLayout labels = new LinearLayout(host);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(host.dp(10), 0, host.dp(6), 0);
        TextView title = host.uiFactory.text(name, 17, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(host.uiFactory.text(tracks.size() + " " + host.tr("songs", "песен"), 13, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, host.dp(62), 1.0f));

        Button play = host.uiFactory.icon(host.playbackQueueController.isPlayingSource(tracks) ? "Ⅱ" : "▶");
        host.uiFactory.applyPlainIconStyle(play, host.purple);
        SongRowStateRegistry.applyPlayState(play, host.playbackQueueController.isPlayingSource(tracks));
        play.setOnClickListener(view -> {
            if (host.playbackQueueController.isPlayingSource(tracks)) {
                host.playbackQueueController.toggleOrStart();
            } else {
                host.playbackQueueController.playList(tracks, false);
            }
        });
        row.addView(play, host.uiFactory.square(44));

        Button shuffle = host.uiFactory.shuffleButton();
        host.uiFactory.applyPlainIconStyle(shuffle);
        shuffle.setOnClickListener(view -> host.playbackQueueController.playList(tracks, true));
        row.addView(shuffle, host.uiFactory.square(44));
        row.setOnClickListener(view -> host.overlayController.openGroup(name, tracks));
        return row;
    }
}
