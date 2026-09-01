package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Playback-dependent Home content kept separate from cached library sections. */
final class HomePlaybackSection extends LinearLayout {
    private final MainActivityCore host;
    private final ImageView cover;
    private final TextView title;
    private final TextView duration;
    private final WaveformView waveform;
    private final Button actions;
    private final Button play;
    private Set<String> staticTrackKeys = Collections.emptySet();
    private Track boundTrack;

    HomePlaybackSection(MainActivityCore host) {
        super(host);
        this.host = host;
        setOrientation(VERTICAL);

        TextView heading = host.uiFactory.text(
                host.tr("Continue listening", "Продолжить прослушивание"), 18, true);
        heading.setPadding(0, host.dp(14), 0, host.dp(4));
        addView(heading, new LinearLayout.LayoutParams(-1, host.dp(50)));

        FrameLayout container = new FrameLayout(host);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(HORIZONTAL);
        row.setGravity(16);
        row.setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4));
        host.uiFactory.applyCardStyle(row, host.appearanceState.songCardOpacity);

        cover = host.uiFactory.coverView();
        row.addView(cover, host.uiFactory.square(52));

        LinearLayout textColumn = new LinearLayout(host);
        textColumn.setOrientation(VERTICAL);
        textColumn.setPadding(host.dp(10), 0, host.dp(6), 0);
        title = host.uiFactory.text("", 16, true);
        title.setTextColor(host.primaryText);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title);

        LinearLayout metaRow = new LinearLayout(host);
        metaRow.setOrientation(HORIZONTAL);
        metaRow.setGravity(16);
        waveform = new WaveformView(host, "", host.purpleSoft, host.yellow, false);
        waveform.setMinimumHeight(host.dp(28));
        waveform.setPadding(0, host.dp(3), 0, host.dp(3));
        metaRow.addView(waveform, new LinearLayout.LayoutParams(0, host.dp(26), 1.0f));
        duration = host.uiFactory.text("", 12, false);
        duration.setGravity(17);
        duration.setTextColor(host.secondaryText);
        metaRow.addView(duration, new LinearLayout.LayoutParams(host.dp(46), host.dp(26)));
        textColumn.addView(metaRow);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, host.dp(62), 1.0f));

        actions = host.uiFactory.icon("⋯");
        host.uiFactory.applyPlainIconStyle(actions);
        row.addView(actions, host.uiFactory.square(44));

        play = host.uiFactory.icon("");
        host.uiFactory.applyPrimaryButtonStyle(play);
        play.setOnClickListener(view -> {
            if (boundTrack == null) return;
            if (host.isCurrent(boundTrack)) {
                host.playbackQueueController.toggleOrStart();
            } else {
                host.playbackQueueController.playTrack(boundTrack);
            }
        });
        row.addView(play, host.uiFactory.square(44));
        container.addView(row, new FrameLayout.LayoutParams(-1, -2));
        addView(host.uiFactory.spaced(container));
        setVisibility(GONE);
    }

    void setStaticTrackKeys(Set<String> keys) {
        staticTrackKeys = Collections.unmodifiableSet(new HashSet<>(keys));
        refresh();
    }

    void refresh() {
        Track current = host.playbackStateProvider.currentTrack();
        if (current == null || staticTrackKeys.contains(key(current))) {
            boundTrack = current;
            waveform.setState(host.purpleSoft, host.yellow, false);
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        if (boundTrack == null || !boundTrack.uri.equals(current.uri)) {
            boundTrack = current;
            title.setText(current.title);
            duration.setText(host.formatTrackDuration(current));
            waveform.setTrackKey(current.title + current.uri);
            View.OnClickListener openOrPlay = view -> TrackTapController.handle(
                    host, current, cover);
            cover.setOnClickListener(openOrPlay);
            actions.setOnClickListener(view -> host.overlayController.openSongActions(current));
            if (isAttachedToWindow()) {
                host.artworkUi.loadUnregisteredCover(
                        cover, current, host.purpleSoft, CoverLoader.THUMB_SIZE);
            }
        }
        waveform.setState(host.purple, host.yellow, host.isPlaybackPlaying());
        SongRowStateRegistry.applyPlayState(play, host.isPlaybackPlaying());
        if (cover instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) cover).updatePlaybackState();
        }
    }

    void setTransitionPaused(boolean paused) {
        waveform.setTransitionPaused(paused);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        boundTrack = null;
        refresh();
    }

    private static String key(Track track) {
        return track.trackId == null || track.trackId.isEmpty() ? track.uri : track.trackId;
    }
}
