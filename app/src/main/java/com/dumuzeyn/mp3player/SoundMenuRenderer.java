package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

final class SoundMenuRenderer extends TrackGroupMenuRenderer {
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
            addRebuildButton();
            return;
        }
        super.render();
        addRebuildButton();
    }

    @Override
    Map<String, ArrayList<Track>> groupedTracks() {
        LinkedHashMap<String, ArrayList<Track>> result = new LinkedHashMap<>();
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
        return tracks.size() + " " + host.tr("tracks", "треков");
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
        } else if (analysis.rebuildingGroups()) {
            status = host.tr("Rebuilding groups", "Пересборка групп");
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

    private void addRebuildButton() {
        SoundAnalysisController analysis = host.soundAnalysisController;
        boolean busy = analysis.rebuildingGroups() || analysis.fullReanalysis();
        String label = analysis.rebuildingGroups()
                ? host.tr("Rebuilding groups...", "Пересборка групп...")
                : host.tr("Rebuild groups", "Пересобрать группы");
        Button button = host.uiFactory.button(label);
        host.uiFactory.applySecondaryButtonStyle(button,
                host.appearanceState.genreCardOpacity);
        button.setEnabled(!busy && analysis.analyzed() >= 4);
        button.setOnClickListener(view -> analysis.rebuildGroupsFromSavedProfiles());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(48));
        params.setMargins(0, host.dp(10), 0, host.dp(4));
        host.list.addView(button, params);
    }

}
