package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Settings UI for persisted SAF music sources. */
final class MusicFoldersController {
    private final MainActivityCore host;

    MusicFoldersController(MainActivityCore host) {
        this.host = host;
    }

    void open() {
        List<LibrarySource> sources = PersistedFolderStore.list(host);
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(
                host.tr("Music folders", "Музыкальные папки")),
                host.uiFactory.dialogTitleParams());

        LinearLayout rows = new LinearLayout(host);
        rows.setOrientation(LinearLayout.VERTICAL);
        if (sources.isEmpty()) {
            TextView empty = host.uiFactory.text(
                    host.tr("No folders added", "Папки не добавлены"), 16, false);
            empty.setTextColor(host.muted);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            rows.addView(empty, new LinearLayout.LayoutParams(-1, host.dp(64)));
        } else {
            for (LibrarySource source : sources) {
                rows.addView(sourceRow(source, shade),
                        new LinearLayout.LayoutParams(-1, host.dp(62)));
            }
        }
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(false);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        int availableScrollHeight = Math.max(host.dp(64),
                host.getResources().getDisplayMetrics().heightPixels - host.dp(250));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(availableScrollHeight,
                        host.dp(Math.max(64, sources.size() * 62)))));

        Button add = host.uiFactory.button(host.tr("Add folder", "Добавить папку"));
        host.uiFactory.applyPrimaryButtonStyle(add);
        add.setOnClickListener(view -> {
            close(shade);
            host.audioImportController.openFolder();
        });
        panel.addView(add, buttonParams());
        Button done = host.uiFactory.button(host.tr("Done", "Готово"));
        done.setOnClickListener(view -> close(shade));
        panel.addView(done, buttonParams());
        shade.addView(panel, host.centerParams(host.dp(350), -2));
        host.overlayHost.addView(shade);
    }

    private LinearLayout sourceRow(LibrarySource source, FrameLayout shade) {
        LinearLayout row = host.uiFactory.row();
        TextView name = host.uiFactory.text(source.displayName, 16, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setContentDescription(source.displayName);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 1.0f));
        Button remove = host.uiFactory.icon("×");
        remove.setContentDescription(host.tr("Remove folder from Voltune: ",
                "Убрать папку из Voltune: ") + source.displayName);
        remove.setOnClickListener(view -> confirmRemoval(source, shade));
        row.addView(remove, new LinearLayout.LayoutParams(host.dp(52), host.dp(52)));
        return row;
    }

    private void confirmRemoval(LibrarySource source, FrameLayout shade) {
        host.showConfirmPanel(
                host.tr("Remove music folder from Voltune?",
                        "Убрать музыкальную папку из Voltune?"),
                host.tr("The folder and all its songs will be removed from Voltune. "
                                + "Files on the device will stay unchanged.",
                        "Папка и все песни из неё будут удалены из Voltune. "
                                + "Файлы на устройстве останутся без изменений."),
                () -> {
                    close(shade);
                    host.playbackQueueController.removeSource(source);
                });
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, host.dp(52));
        params.setMargins(0, host.dp(8), 0, 0);
        return params;
    }

    private void close(FrameLayout shade) {
        if (shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        host.playerUiController.updateMini();
    }
}
