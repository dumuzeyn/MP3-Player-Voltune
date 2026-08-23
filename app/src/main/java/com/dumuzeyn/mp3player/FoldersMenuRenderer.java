package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Map;

final class FoldersMenuRenderer implements MenuRenderer {
    private final MainActivityCore host;

    FoldersMenuRenderer(MainActivityCore host) {
        this.host = host;
    }

    @Override
    public void render() {
        Map<String, ArrayList<Track>> folders = host.libraryState.homeContent.folders;
        if (folders.isEmpty()) {
            host.list.addView(host.uiFactory.text(host.tr(
                    "No imported folders", "Нет импортированных папок"), 17, false));
            return;
        }
        for (Map.Entry<String, ArrayList<Track>> entry : folders.entrySet()) {
            host.list.addView(folderRow(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public boolean needsMiniSpacer() {
        return true;
    }

    private View folderRow(String name, ArrayList<Track> tracks) {
        LinearLayout row = host.uiFactory.row();
        row.setPadding(host.dp(10), host.dp(5), host.dp(6), host.dp(5));
        host.uiFactory.applyCardStyle(row, host.appearanceState.songCardOpacity);
        LinearLayout labels = new LinearLayout(host);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = host.uiFactory.text(name, 17, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(host.uiFactory.text(tracks.size() + " "
                + host.tr("songs", "песен"), 13, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, host.dp(62), 1.0f));
        Button add = host.uiFactory.icon("+");
        add.setContentDescription(host.tr("Add folder to queue",
                "Добавить папку в очередь"));
        add.setOnClickListener(view -> host.playbackQueueController.addAll(tracks));
        row.addView(add, host.uiFactory.square(44));
        Button shuffle = host.uiFactory.shuffleButton();
        shuffle.setContentDescription(host.tr("Shuffle folder", "Перемешать папку"));
        shuffle.setOnClickListener(view -> host.playbackQueueController.playList(tracks, true));
        row.addView(shuffle, host.uiFactory.square(44));
        Button play = host.uiFactory.icon("▶");
        play.setContentDescription(host.tr("Play folder", "Воспроизвести папку"));
        play.setOnClickListener(view -> host.playbackQueueController.playList(tracks, false));
        row.addView(play, host.uiFactory.square(44));
        row.setOnClickListener(view -> host.overlayController.openGroup(name, tracks));
        return host.uiFactory.spaced(row);
    }
}
