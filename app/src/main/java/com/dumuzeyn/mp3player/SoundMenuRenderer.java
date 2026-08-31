package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class SoundMenuRenderer extends TrackGroupMenuRenderer {
    private final Map<String, TempoSummary> summaries = new HashMap<>();

    SoundMenuRenderer(MainActivityCore host) {
        super(host);
    }

    @Override
    public void render() {
        addStatus();
        if (!host.soundAnalysisController.enabled()) {
            addMessage(host.tr("Similar-track analysis is disabled in Settings",
                    "Анализ похожих треков выключен в настройках"));
            return;
        }
        if (host.libraryState.tracks.size() < 4) {
            addMessage(host.tr("Add more songs to find similar tracks",
                    "Добавьте больше песен, чтобы найти похожие треки"));
            return;
        }
        if (host.soundAnalysisController.groups().isEmpty()) {
            addMessage(host.tr("Similar tracks will appear after local analysis",
                    "Похожие треки появятся после локального анализа"));
            return;
        }
        super.render();
    }

    @Override
    Map<String, ArrayList<Track>> groupedTracks() {
        LinkedHashMap<String, ArrayList<Track>> result = new LinkedHashMap<>();
        summaries.clear();
        boolean english = "en".equals(host.appearanceState.language);
        for (SoundGroup group : host.soundAnalysisController.groups()) {
            ArrayList<Track> tracks = new ArrayList<>();
            for (String trackId : group.trackIds) {
                Track track = host.findTrack(trackId);
                if (track != null) {
                    tracks.add(track);
                }
            }
            if (!tracks.isEmpty()) {
                String name = english ? group.nameEnglish : group.nameRussian;
                ArrayList<Track> namedTracks = result.get(name);
                if (namedTracks == null) {
                    namedTracks = new ArrayList<>();
                    result.put(name, namedTracks);
                }
                namedTracks.addAll(tracks);
                TempoSummary summary = summaries.get(name);
                if (summary == null) {
                    summary = new TempoSummary();
                    summaries.put(name, summary);
                }
                summary.add(group);
            }
        }
        return result;
    }

    @Override
    String unknownGroupName() {
        return host.tr("Similar tracks", "Похожие треки");
    }

    @Override
    String groupSubtitle(String name, ArrayList<Track> tracks) {
        TempoSummary summary = summaries.get(name);
        String count = tracks.size() + " " + host.tr("tracks", "треков");
        if (summary == null || summary.weight == 0.0d) {
            return count;
        }
        return Math.round(summary.weightedBpm / summary.weight) + " BPM · " + count;
    }

    @Override
    int cardOpacity() {
        return host.appearanceState.genreCardOpacity;
    }

    private void addStatus() {
        SoundAnalysisController analysis = host.soundAnalysisController;
        String status;
        if (!analysis.enabled()) {
            status = host.tr("Analysis disabled", "Анализ выключен");
        } else if (!analysis.activeTitle().isEmpty()) {
            status = host.tr("Analyzing: ", "Анализ: ") + analysis.activeTitle();
        } else if (analysis.blockReason() != SoundAnalysisConstraints.BlockReason.NONE) {
            status = blockedText(analysis.blockReason());
        } else if (analysis.queued() > 0) {
            status = host.tr("Waiting: ", "В очереди: ") + analysis.queued();
        } else {
            status = host.tr("Similar tracks are up to date", "Похожие треки актуальны");
        }
        String progress = analysis.analyzed() + " / " + analysis.total();
        if (analysis.failed() > 0) {
            progress += host.tr(" · errors: ", " · ошибок: ") + analysis.failed();
        }
        LinearLayout block = new LinearLayout(host);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(host.dp(12), host.dp(7), host.dp(12), host.dp(9));
        TextView title = host.uiFactory.text(status, 15, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        block.addView(title);
        block.addView(host.uiFactory.text(progress, 13, false));
        host.list.addView(block, new LinearLayout.LayoutParams(-1, host.dp(58)));
    }

    private String blockedText(SoundAnalysisConstraints.BlockReason reason) {
        if (reason == SoundAnalysisConstraints.BlockReason.PLAYBACK) {
            return host.tr("Paused during playback", "Пауза во время воспроизведения");
        }
        if (reason == SoundAnalysisConstraints.BlockReason.LOW_BATTERY) {
            return host.tr("Paused to save battery", "Пауза для экономии заряда");
        }
        return host.tr("Paused until the device cools down",
                "Пауза до охлаждения устройства");
    }

    private void addMessage(String value) {
        TextView message = host.uiFactory.text(value, 15, false);
        message.setTextColor(host.secondaryText);
        message.setGravity(android.view.Gravity.CENTER);
        message.setPadding(host.dp(20), host.dp(12), host.dp(20), host.dp(12));
        host.list.addView(message, new LinearLayout.LayoutParams(-1, host.dp(82)));
    }

    private static final class TempoSummary {
        double weightedBpm;
        double weight;

        void add(SoundGroup group) {
            if (group.centroid.length <= TrackAudioProfile.TEMPO_CONFIDENCE) {
                return;
            }
            double bpm = group.centroid[TrackAudioProfile.BPM];
            double confidence = group.centroid[TrackAudioProfile.TEMPO_CONFIDENCE];
            if (bpm <= 0.0d || confidence < 0.45d) {
                return;
            }
            int size = Math.max(1, group.trackIds.size());
            weightedBpm += bpm * size;
            weight += size;
        }
    }
}
