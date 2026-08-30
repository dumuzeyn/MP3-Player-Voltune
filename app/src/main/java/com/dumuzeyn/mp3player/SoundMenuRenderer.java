package com.dumuzeyn.mp3player;

import android.text.TextUtils;
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
            addMessage(host.tr("Sound analysis is disabled in Settings",
                    "Анализ звучания выключен в настройках"));
            return;
        }
        if (host.libraryState.tracks.size() < 4) {
            addMessage(host.tr("Add more songs to create sound groups",
                    "Добавьте больше песен для групп по звучанию"));
            return;
        }
        if (host.soundAnalysisController.groups().isEmpty()) {
            addMessage(host.tr("Groups will appear as audio profiles become ready",
                    "Группы появятся по мере готовности звуковых профилей"));
            return;
        }
        super.render();
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
                result.put(english ? group.nameEnglish : group.nameRussian, tracks);
            }
        }
        return result;
    }

    @Override
    String unknownGroupName() {
        return host.tr("Sound group", "Группа звучания");
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
            status = host.tr("Sound profiles are up to date", "Звуковые профили актуальны");
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
}
