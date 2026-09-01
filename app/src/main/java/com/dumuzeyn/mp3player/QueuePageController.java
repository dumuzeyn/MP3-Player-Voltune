package com.dumuzeyn.mp3player;

import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;

/** Reuses one diffing queue adapter for the lifetime of the full player. */
final class QueuePageController implements AutoCloseable {
    private final MainActivityCore host;
    private final PlaybackStateProvider state;
    private final Runnable navigateBack;
    private LinearLayout root;
    private QueueAdapter adapter;
    private boolean active;

    QueuePageController(MainActivityCore host, PlaybackStateProvider state,
            Runnable navigateBack) {
        this.host = host;
        this.state = state;
        this.navigateBack = navigateBack;
    }

    View createView() {
        if (root != null) {
            return root;
        }
        root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(host.dp(6), host.dp(4), host.dp(6), host.dp(8));
        root.addView(actionsRow(), new LinearLayout.LayoutParams(-1, host.dp(58)));
        adapter = new QueueAdapter(host, state.activeQueue(), listener());
        RecyclerView list = new RecyclerView(host);
        list.setLayoutManager(new LinearLayoutManager(host));
        list.setAdapter(adapter);
        list.setHasFixedSize(false);
        attachGestures(list);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return root;
    }

    void setActive(boolean value) {
        active = value;
        if (value) refresh();
    }

    void refresh() {
        if (active && adapter != null) {
            adapter.submitTracks(state.activeQueue());
            adapter.refreshPlayback();
        }
    }

    private LinearLayout actionsRow() {
        LinearLayout row = host.uiFactory.row();
        row.setGravity(Gravity.CENTER);
        Button save = host.uiFactory.icon("▣");
        save.setContentDescription(host.tr("Save queue as playlist",
                "Сохранить очередь как плейлист"));
        save.setOnClickListener(view -> saveQueue());
        row.addView(save, host.uiFactory.square(52));
        Button clear = host.uiFactory.icon("⌫");
        clear.setContentDescription(host.tr("Clear queue", "Очистить очередь"));
        clear.setOnClickListener(view -> {
            host.playbackQueueController.clear();
            refresh();
        });
        row.addView(clear, host.uiFactory.square(52));
        Button add = host.uiFactory.icon("+");
        add.setContentDescription(host.tr("Add to queue", "Добавить в очередь"));
        add.setOnClickListener(view -> chooseTracks());
        row.addView(add, host.uiFactory.square(52));
        return row;
    }

    private QueueAdapter.Listener listener() {
        return new QueueAdapter.Listener() {
            @Override public void remove(int index) {
                host.playbackController.removeQueueItem(index);
            }
            @Override public void play(int index) {
                host.playbackQueueController.seekIndex(index);
            }
            @Override public void move(int from, int to) {
                host.playbackQueueController.move(from, to);
            }
        };
    }

    private void attachGestures(RecyclerView list) {
        list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float downX;
            private float downY;
            private boolean navigating;

            @Override public boolean onInterceptTouchEvent(
                    RecyclerView view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    navigating = false;
                    return false;
                }
                if (event.getActionMasked() != MotionEvent.ACTION_MOVE || navigating) {
                    return navigating;
                }
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (dx >= host.dp(42) && dx > Math.abs(dy) * 1.25f) {
                    navigating = true;
                    navigateBack.run();
                    return true;
                }
                return false;
            }

            @Override public void onTouchEvent(RecyclerView view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    navigating = false;
                }
            }
        });
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT) {
            @Override public boolean onMove(RecyclerView view, RecyclerView.ViewHolder from,
                    RecyclerView.ViewHolder to) {
                return adapter.move(from.getBindingAdapterPosition(),
                        to.getBindingAdapterPosition());
            }
            @Override public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
                adapter.remove(holder.getBindingAdapterPosition());
            }
        });
        helper.attachToRecyclerView(list);
    }

    private void chooseTracks() {
        host.overlayController.openSelection(host.tr("Add to queue", "Добавить в очередь"),
                new HashSet<>(), selected -> {
                    ArrayList<Track> additions = new ArrayList<>();
                    for (String uri : selected) {
                        Track track = host.findTrack(uri);
                        if (track != null) additions.add(track);
                    }
                    host.playbackQueueController.addAll(additions);
                    refresh();
                });
    }

    private void saveQueue() {
        if (state.activeQueue().isEmpty()) return;
        host.overlayController.showInput(host.tr("Save queue", "Сохранить очередь"),
                host.tr("Playlist name", "Название плейлиста"), "", false, value -> {
                    Playlist playlist = host.playlistController.createPlaylist(value);
                    if (playlist != null) {
                        for (Track track : state.activeQueue()) playlist.uris.add(track.uri);
                        host.saveLibraryState();
                    }
                });
    }

    @Override public void close() {
        active = false;
        if (adapter != null) adapter.submitTracks(new ArrayList<>());
        adapter = null;
        root = null;
    }
}
