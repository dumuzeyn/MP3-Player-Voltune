package com.dumuzeyn.mp3player;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

final class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {
    interface Listener {
        void remove(int index);
        void play(int index);
        void move(int from, int to);
    }

    private final MainActivityCore host;
    private final Listener listener;
    private final ArrayList<Track> tracks;

    QueueAdapter(MainActivityCore host, List<Track> tracks, Listener listener) {
        this.host = host;
        this.listener = listener;
        this.tracks = new ArrayList<>(tracks);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return tracks.get(position).trackId.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(host);
        container.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        return new Holder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.container.removeAllViews();
        Track track = tracks.get(position);
        holder.container.addView(host.songsRenderer.queueRow(track,
                () -> remove(holder.getBindingAdapterPosition()),
                () -> listener.play(holder.getBindingAdapterPosition())),
                new FrameLayout.LayoutParams(-1, -2));
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    boolean move(int from, int to) {
        if (from < 0 || to < 0 || from >= tracks.size() || to >= tracks.size()) {
            return false;
        }
        Track moved = tracks.remove(from);
        tracks.add(to, moved);
        notifyItemMoved(from, to);
        listener.move(from, to);
        return true;
    }

    void remove(int position) {
        if (position < 0 || position >= tracks.size()) {
            return;
        }
        tracks.remove(position);
        notifyItemRemoved(position);
        listener.remove(position);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout container;

        Holder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }
}
