package com.dumuzeyn.mp3player;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

final class SongRowStateRegistry {
    interface StateResolver {
        Track findTrack(String uri);
        boolean isCurrent(Track track);
        boolean isPlaying();
        int activeColor();
        int secondaryActiveColor();
        int inactiveColor();
    }

    private final HashMap<String, Button> playButtons = new HashMap<>();
    private final HashMap<String, View> currentMarkers = new HashMap<>();
    private final HashMap<String, WaveformView> waveforms = new HashMap<>();
    private final HashMap<String, ArrayList<RotatingCoverImageView>> covers = new HashMap<>();
    private final HashMap<String, TextView> titles = new HashMap<>();
    private final HashMap<String, TextView> durations = new HashMap<>();

    void clear() {
        playButtons.clear();
        currentMarkers.clear();
        waveforms.clear();
        covers.clear();
        titles.clear();
        durations.clear();
    }

    void replaceWith(SongRowStateRegistry source) {
        clear();
        playButtons.putAll(source.playButtons);
        currentMarkers.putAll(source.currentMarkers);
        waveforms.putAll(source.waveforms);
        titles.putAll(source.titles);
        durations.putAll(source.durations);
        for (Map.Entry<String, ArrayList<RotatingCoverImageView>> entry : source.covers.entrySet()) {
            covers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    void forEachCover(CoverConsumer consumer) {
        for (Map.Entry<String, ArrayList<RotatingCoverImageView>> entry : covers.entrySet()) {
            for (RotatingCoverImageView cover : entry.getValue()) {
                consumer.accept(entry.getKey(), cover);
            }
        }
    }

    interface CoverConsumer {
        void accept(String uri, RotatingCoverImageView cover);
    }

    void registerPlayButton(String uri, Button button) {
        playButtons.put(uri, button);
    }

    void registerCurrentMarker(String uri, View marker) {
        currentMarkers.put(uri, marker);
    }

    void registerWaveform(String uri, WaveformView waveform) {
        waveforms.put(uri, waveform);
    }

    void registerCover(String uri, RotatingCoverImageView cover) {
        ArrayList<RotatingCoverImageView> registered = covers.get(uri);
        if (registered == null) {
            registered = new ArrayList<>();
            covers.put(uri, registered);
        }
        if (!registered.contains(cover)) {
            registered.add(cover);
        }
    }

    void registerMetadata(String uri, TextView title, TextView duration) {
        titles.put(uri, title);
        durations.put(uri, duration);
    }

    void refreshMetadata(String uri, Track track, String durationText) {
        TextView title = titles.get(uri);
        if (title != null) {
            title.setText(track.title);
        }
        TextView duration = durations.get(uri);
        if (duration != null) {
            duration.setText(durationText);
        }
    }

    static void applyPlayState(Button button, boolean playing) {
        button.setText(playing ? "\u2161" : "\u25b6");
        int opticalOffset = playing ? 0 : Math.round(
                button.getResources().getDisplayMetrics().density * 2.0f);
        button.setPadding(opticalOffset, 0, 0, 0);
    }

    void refresh(StateResolver resolver) {
        for (Map.Entry<String, Button> entry : playButtons.entrySet()) {
            Track track = resolver.findTrack(entry.getKey());
            boolean current = track != null && resolver.isCurrent(track);
            applyPlayState(entry.getValue(), current && resolver.isPlaying());
        }
        for (Map.Entry<String, View> entry : currentMarkers.entrySet()) {
            Track track = resolver.findTrack(entry.getKey());
            entry.getValue().setVisibility(track != null && resolver.isCurrent(track) ? View.VISIBLE : View.INVISIBLE);
        }
        for (Map.Entry<String, WaveformView> entry : waveforms.entrySet()) {
            Track track = resolver.findTrack(entry.getKey());
            boolean current = track != null && resolver.isCurrent(track);
            entry.getValue().setState(
                    current ? resolver.activeColor() : resolver.inactiveColor(),
                    resolver.secondaryActiveColor(),
                    current && resolver.isPlaying());
        }
        for (ArrayList<RotatingCoverImageView> registered : covers.values()) {
            for (RotatingCoverImageView cover : registered) {
                cover.updatePlaybackState();
            }
        }
    }
}
