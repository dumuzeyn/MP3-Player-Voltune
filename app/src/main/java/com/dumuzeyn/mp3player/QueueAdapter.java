package com.dumuzeyn.mp3player;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

final class QueueAdapter extends ListAdapter<Track, QueueAdapter.Holder> {
    interface Listener {
        void remove(int index);
        void play(int index);
        void move(int from, int to);
    }

    private final MainActivityCore host;
    private final Listener listener;
    private static final DiffUtil.ItemCallback<Track> DIFF =
            new DiffUtil.ItemCallback<Track>() {
                @Override public boolean areItemsTheSame(@NonNull Track oldItem,
                        @NonNull Track newItem) {
                    return oldItem.trackId.equals(newItem.trackId);
                }
                @Override public boolean areContentsTheSame(@NonNull Track oldItem,
                        @NonNull Track newItem) {
                    return oldItem.uri.equals(newItem.uri)
                            && oldItem.title.equals(newItem.title);
                }
            };

    QueueAdapter(MainActivityCore host, List<Track> tracks, Listener listener) {
        super(DIFF);
        this.host = host;
        this.listener = listener;
        setHasStableIds(true);
        submitTracks(tracks);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).trackId.hashCode();
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
        Track track = getItem(position);
        holder.container.addView(host.songsRenderer.queueRow(track,
                () -> remove(holder.getBindingAdapterPosition()),
                () -> listener.play(holder.getBindingAdapterPosition())),
                new FrameLayout.LayoutParams(-1, -2));
    }

    void submitTracks(List<Track> tracks) {
        submitList(new ArrayList<>(tracks));
    }

    boolean move(int from, int to) {
        if (from < 0 || to < 0 || from >= getItemCount() || to >= getItemCount()) {
            return false;
        }
        ArrayList<Track> tracks = new ArrayList<>(getCurrentList());
        Track moved = tracks.remove(from);
        tracks.add(to, moved);
        submitList(tracks);
        listener.move(from, to);
        return true;
    }

    void remove(int position) {
        if (position < 0 || position >= getItemCount()) {
            return;
        }
        ArrayList<Track> tracks = new ArrayList<>(getCurrentList());
        tracks.remove(position);
        submitList(tracks);
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
