package com.dumuzeyn.mp3player;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;

/** Queue panel backed exclusively by Media3 queue commands. */
final class QueueOverlayController {
    private final MainActivityCore host;
    private final OverlayController overlays;

    QueueOverlayController(MainActivityCore host, OverlayController overlays) {
        this.host = host;
        this.overlays = overlays;
    }

    void open() {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.addView(header(shade));
        ArrayList<Track> snapshot = new ArrayList<>(host.playbackUiState.queue);
        QueueAdapter adapter = adapter(snapshot);
        RecyclerView list = new RecyclerView(host);
        list.setLayoutManager(new LinearLayoutManager(host));
        list.setAdapter(adapter);
        list.setHasFixedSize(false);
        attachGestures(list, adapter);
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        shade.addView(panel, host.bottomParams());
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private LinearLayout header(FrameLayout shade) {
        LinearLayout header = host.uiFactory.row();
        header.addView(host.uiFactory.text(host.tr("Queue", "Очередь"), 20, true),
                new LinearLayout.LayoutParams(0, host.dp(58), 1.0f));
        Button save = host.uiFactory.icon("▣");
        save.setContentDescription(host.tr("Save queue as playlist",
                "Сохранить очередь как плейлист"));
        save.setOnClickListener(view -> saveQueue(shade));
        header.addView(save, host.uiFactory.square(48));
        Button clear = host.uiFactory.icon("⌫");
        clear.setContentDescription(host.tr("Clear queue", "Очистить очередь"));
        clear.setOnClickListener(view -> {
            host.playbackQueueController.clear();
            close(shade);
        });
        header.addView(clear, host.uiFactory.square(48));
        Button add = host.uiFactory.icon("+");
        add.setContentDescription(host.tr("Add to queue", "Добавить в очередь"));
        add.setOnClickListener(view -> chooseTracks(shade));
        header.addView(add, host.uiFactory.square(48));
        Button close = host.uiFactory.icon("×");
        close.setContentDescription(host.tr("Close", "Закрыть"));
        close.setOnClickListener(view -> close(shade));
        header.addView(close, host.uiFactory.square(48));
        return header;
    }

    private QueueAdapter adapter(ArrayList<Track> tracks) {
        return new QueueAdapter(host, tracks, new QueueAdapter.Listener() {
            @Override
            public void remove(int index) {
                host.playbackController.removeQueueItem(index);
            }

            @Override
            public void play(int index) {
                host.playbackQueueController.seekIndex(index);
            }

            @Override
            public void move(int from, int to) {
                host.playbackQueueController.move(from, to);
            }
        });
    }

    private void attachGestures(RecyclerView list, QueueAdapter adapter) {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source,
                    RecyclerView.ViewHolder target) {
                return adapter.move(source.getBindingAdapterPosition(),
                        target.getBindingAdapterPosition());
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
                adapter.remove(holder.getBindingAdapterPosition());
            }
        });
        helper.attachToRecyclerView(list);
    }

    private void chooseTracks(FrameLayout shade) {
        close(shade);
        overlays.openSelection(host.tr("Add to queue", "Добавить в очередь"),
                new HashSet<>(), selected -> {
                    ArrayList<Track> additions = new ArrayList<>();
                    for (String uri : selected) {
                        Track track = host.findTrack(uri);
                        if (track != null) {
                            additions.add(track);
                        }
                    }
                    host.playbackQueueController.addAll(additions);
                    host.uiHandler.postDelayed(this::open, 120L);
                });
    }

    private void saveQueue(FrameLayout shade) {
        if (host.playbackUiState.queue.isEmpty()) {
            return;
        }
        close(shade);
        overlays.showInput(host.tr("Save queue", "Сохранить очередь"),
                host.tr("Playlist name", "Название плейлиста"), "", false, value -> {
                    Playlist playlist = host.playlistController.createPlaylist(value);
                    if (playlist != null) {
                        for (Track track : host.playbackUiState.queue) {
                            playlist.uris.add(track.uri);
                        }
                        host.saveLibraryState();
                    }
                });
    }

    private void close(FrameLayout shade) {
        if (shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        host.playerUiController.updateMini();
    }
}
