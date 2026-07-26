package com.dumuzeyn.mp3player;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Persistent, recyclable surface for the main Songs tab. */
final class SongsView extends FrameLayout implements AutoCloseable {
    private final MainActivityCore host;
    private final RecyclerView recyclerView;
    private final SongAdapter songAdapter;
    private final HeaderAdapter headerAdapter;
    private final EmptyAdapter emptyAdapter;
    private final String searchOwner;
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (getVisibility() != View.VISIBLE) {
                return;
            }
            if (host.isPlaybackPlaying()) {
                int currentIndex = host.currentTrackIndex();
                Track current = currentIndex >= 0
                        && currentIndex < host.libraryState.tracks.size()
                        ? host.libraryState.tracks.get(currentIndex) : null;
                songAdapter.refreshPosition(host.playbackPosition(),
                        current == null ? 0L : host.playbackDurationFor(current));
                host.uiHandler.postDelayed(this, 500L);
            }
        }
    };

    private ArrayList<Track> sourceSnapshot = new ArrayList<>();
    private String query = "";
    private boolean closed;

    SongsView(MainActivityCore host) {
        super(host);
        this.host = host;
        this.searchOwner = "songs-" + Integer.toHexString(System.identityHashCode(this));
        this.songAdapter = new SongAdapter(host);
        this.headerAdapter = new HeaderAdapter();
        this.emptyAdapter = new EmptyAdapter();

        recyclerView = new RecyclerView(host);
        LinearLayoutManager layoutManager = new LinearLayoutManager(host);
        layoutManager.setRecycleChildrenOnDetach(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemViewCacheSize(6);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, 0, 0, host.dp(88));
        recyclerView.setItemAnimator(null);
        ConcatAdapter.Config config = new ConcatAdapter.Config.Builder()
                .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                .build();
        recyclerView.setAdapter(new ConcatAdapter(
                config, headerAdapter, songAdapter, emptyAdapter));
        addView(recyclerView, new FrameLayout.LayoutParams(-1, -1));
        setVisibility(View.GONE);
    }

    void show(List<Track> source, String searchQuery) {
        setVisibility(View.VISIBLE);
        submit(source, searchQuery, false);
        headerAdapter.refresh();
        updateProgressTicker();
    }

    void prepareForTransition(List<Track> source, String searchQuery) {
        setVisibility(View.VISIBLE);
        submit(source, searchQuery, false);
        headerAdapter.refresh();
    }

    void hide() {
        setTranslationX(0.0f);
        setVisibility(View.GONE);
        host.uiHandler.removeCallbacks(progressTicker);
    }

    void refreshPlayback() {
        songAdapter.refreshPlayback();
        headerAdapter.refresh();
        updateProgressTicker();
    }

    void refreshMetadata(Track track) {
        songAdapter.replaceTrack(track);
    }

    void refreshFilteredSource(List<Track> source) {
        submit(source, query, true);
    }

    List<Track> visibleTracks() {
        return songAdapter.visibleTracks();
    }

    RecyclerView recyclerView() {
        return recyclerView;
    }

    String query() {
        return query;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        host.uiHandler.removeCallbacks(progressTicker);
        host.trackSearchController.cancel(searchOwner);
        recyclerView.setAdapter(null);
    }

    private void submit(List<Track> source, String searchQuery, boolean force) {
        if (closed) {
            return;
        }
        String normalizedQuery = Track.normalizeSearchText(searchQuery);
        if (!force && normalizedQuery.equals(query) && sameSnapshot(sourceSnapshot, source)) {
            return;
        }
        query = normalizedQuery;
        sourceSnapshot = new ArrayList<>(source);
        host.trackSearchController.filter(searchOwner, sourceSnapshot, normalizedQuery,
                filtered -> {
                    if (closed) {
                        return;
                    }
                    songAdapter.submitList(new ArrayList<>(filtered),
                            () -> {
                                emptyAdapter.setEmpty(filtered.isEmpty());
                                headerAdapter.refresh();
                            });
                });
    }

    private void updateProgressTicker() {
        host.uiHandler.removeCallbacks(progressTicker);
        if (getVisibility() == View.VISIBLE && host.isPlaybackPlaying()) {
            host.uiHandler.post(progressTicker);
        }
    }

    private static boolean sameSnapshot(List<Track> left, List<Track> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            Track first = left.get(index);
            Track second = right.get(index);
            if (!Objects.equals(first.trackId, second.trackId)
                    || first != second && (!Objects.equals(first.uri, second.uri)
                    || !Objects.equals(first.title, second.title)
                    || first.durationMs != second.durationMs)) {
                return false;
            }
        }
        return true;
    }

    private final class HeaderAdapter extends RecyclerView.Adapter<HeaderHolder> {
        HeaderAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return 1L;
        }

        @NonNull
        @Override
        public HeaderHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new HeaderHolder(host.headerController.createSongsSectionHeader());
        }

        @Override
        public void onBindViewHolder(@NonNull HeaderHolder holder, int position) {
            host.headerController.refreshSongsSectionHeader(holder.itemView);
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        void refresh() {
            notifyItemChanged(0);
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        HeaderHolder(View itemView) {
            super(itemView);
        }
    }

    private final class EmptyAdapter extends RecyclerView.Adapter<EmptyHolder> {
        private boolean empty = true;

        EmptyAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return 2L;
        }

        @NonNull
        @Override
        public EmptyHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = host.uiFactory.text("", 18, true);
            text.setPadding(host.dp(12), host.dp(24), host.dp(12), host.dp(24));
            return new EmptyHolder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull EmptyHolder holder, int position) {
            holder.text.setText(host.navigationState.search.trim().isEmpty()
                    ? host.tr("Add MP3 or another audio file",
                            "Добавьте MP3 или другой аудиофайл")
                    : host.tr("Nothing found", "Ничего не найдено"));
        }

        @Override
        public int getItemCount() {
            return empty ? 1 : 0;
        }

        void setEmpty(boolean value) {
            if (empty == value) {
                if (empty) {
                    notifyItemChanged(0);
                }
                return;
            }
            empty = value;
            if (empty) {
                notifyItemInserted(0);
            } else {
                notifyItemRemoved(0);
            }
        }
    }

    private static final class EmptyHolder extends RecyclerView.ViewHolder {
        final TextView text;

        EmptyHolder(TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }
}
