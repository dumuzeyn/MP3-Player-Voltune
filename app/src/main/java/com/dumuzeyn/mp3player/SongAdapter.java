package com.dumuzeyn.mp3player;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Recycles song rows and applies small state changes through payload binds. */
final class SongAdapter extends ListAdapter<Track, SongAdapter.SongViewHolder> {
    static final int PAYLOAD_PLAYBACK = 1;
    static final int PAYLOAD_POSITION = 1 << 1;
    static final int PAYLOAD_METADATA = 1 << 2;
    static final int PAYLOAD_ARTWORK = 1 << 3;

    private static final DiffUtil.ItemCallback<Track> TRACK_DIFF =
            new DiffUtil.ItemCallback<Track>() {
                @Override
                public boolean areItemsTheSame(@NonNull Track oldItem,
                        @NonNull Track newItem) {
                    return oldItem.trackId.equals(newItem.trackId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Track oldItem,
                        @NonNull Track newItem) {
                    return contentPayload(oldItem, newItem) == 0;
                }

                @Override
                public Object getChangePayload(@NonNull Track oldItem,
                        @NonNull Track newItem) {
                    int payload = contentPayload(oldItem, newItem);
                    return payload == 0 ? null : payload;
                }
            };

    private final MainActivityCore host;
    private final Map<String, Integer> positionsByTrackId = new HashMap<>();
    private String currentTrackId = "";
    private boolean playing;
    private long playbackPositionMs;
    private long playbackDurationMs;

    SongAdapter(MainActivityCore host) {
        super(TRACK_DIFF);
        this.host = host;
        setHasStableIds(true);
        capturePlaybackState();
    }

    @Override
    public long getItemId(int position) {
        return stableLongId(getItem(position).trackId);
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        int payload = 0;
        for (Object item : payloads) {
            if (item instanceof Integer) {
                payload |= (Integer) item;
            }
        }
        holder.bindPayload(getItem(position), payload);
    }

    @Override
    public void onViewRecycled(@NonNull SongViewHolder holder) {
        holder.recycle();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull SongViewHolder holder) {
        holder.updatePlayback();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull SongViewHolder holder) {
        holder.pauseDetachedAnimations();
    }

    @Override
    public void onCurrentListChanged(@NonNull List<Track> previousList,
            @NonNull List<Track> currentList) {
        positionsByTrackId.clear();
        for (int index = 0; index < currentList.size(); index++) {
            positionsByTrackId.put(currentList.get(index).trackId, index);
        }
    }

    void refreshPlayback() {
        String previousTrackId = currentTrackId;
        boolean previousPlaying = playing;
        capturePlaybackState();
        if (!previousTrackId.equals(currentTrackId)) {
            notifyTrack(previousTrackId, PAYLOAD_PLAYBACK | PAYLOAD_POSITION);
            notifyTrack(currentTrackId, PAYLOAD_PLAYBACK | PAYLOAD_POSITION);
        } else if (previousPlaying != playing) {
            notifyTrack(currentTrackId, PAYLOAD_PLAYBACK);
        }
    }

    void replaceTrack(Track updated) {
        if (updated == null) {
            return;
        }
        List<Track> current = getCurrentList();
        for (int index = 0; index < current.size(); index++) {
            if (current.get(index).trackId.equals(updated.trackId)) {
                ArrayList<Track> replacement = new ArrayList<>(current);
                replacement.set(index, updated);
                submitList(replacement);
                return;
            }
        }
    }

    void refreshPosition(long positionMs, long durationMs) {
        playbackPositionMs = Math.max(0L, positionMs);
        playbackDurationMs = Math.max(0L, durationMs);
        notifyTrack(currentTrackId, PAYLOAD_POSITION);
    }

    List<Track> visibleTracks() {
        return new ArrayList<>(getCurrentList());
    }

    private void capturePlaybackState() {
        int index = host.currentTrackIndex();
        Track current = index >= 0 && index < host.libraryState.tracks.size()
                ? host.libraryState.tracks.get(index) : null;
        currentTrackId = current == null ? "" : current.trackId;
        playing = host.isPlaybackPlaying();
        playbackPositionMs = host.playbackPosition();
        playbackDurationMs = current == null ? 0L : host.playbackDurationFor(current);
    }

    private void notifyTrack(String trackId, int payload) {
        Integer position = positionsByTrackId.get(trackId);
        if (position != null) {
            notifyItemChanged(position, payload);
        }
    }

    private static int contentPayload(Track oldItem, Track newItem) {
        int payload = 0;
        if (!Objects.equals(oldItem.title, newItem.title)
                || !Objects.equals(oldItem.artist, newItem.artist)
                || !Objects.equals(oldItem.album, newItem.album)
                || !Objects.equals(oldItem.genre, newItem.genre)
                || oldItem.durationMs != newItem.durationMs) {
            payload |= PAYLOAD_METADATA;
        }
        if (!Objects.equals(oldItem.uri, newItem.uri)
                || oldItem.fileSize != newItem.fileSize
                || oldItem.lastModified != newItem.lastModified
                || !Objects.equals(oldItem.fingerprint, newItem.fingerprint)) {
            payload |= PAYLOAD_ARTWORK;
        }
        return payload;
    }

    private static long stableLongId(String trackId) {
        long result = 0xcbf29ce484222325L;
        for (int index = 0; index < trackId.length(); index++) {
            result ^= trackId.charAt(index);
            result *= 0x100000001b3L;
        }
        return result;
    }

    final class SongViewHolder extends RecyclerView.ViewHolder {
        private final View card;
        private final GradientDrawable cardBackground;
        private final FrameLayout coverContainer;
        private final RotatingCoverImageView cover;
        private final FrameLayout waveformContainer;
        private final WaveformView waveform;
        private final TextView title;
        private final TextView duration;
        private final Button actions;
        private final Button play;
        private final View marker;
        private Track boundTrack;

        SongViewHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.song_card);
            coverContainer = itemView.findViewById(R.id.song_cover_container);
            waveformContainer = itemView.findViewById(R.id.song_waveform_container);
            title = itemView.findViewById(R.id.song_title);
            duration = itemView.findViewById(R.id.song_duration);
            actions = itemView.findViewById(R.id.song_actions);
            play = itemView.findViewById(R.id.song_play);
            marker = itemView.findViewById(R.id.song_current_marker);

            cardBackground = new GradientDrawable();
            cardBackground.setColor(host.cardSurfaceColor(
                    host.card, host.appearanceState.songCardOpacity));
            cardBackground.setCornerRadius(host.dp(14));
            cardBackground.setStroke(host.dp(1), host.cardStroke);
            card.setBackground(cardBackground);
            TextOutlinePolicy.markCardSurface(card, true);

            cover = new RotatingCoverImageView(host);
            cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            coverContainer.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            waveform = new WaveformView(host, "", host.purpleSoft, host.yellow, false);
            waveform.setPadding(0, host.dp(3), 0, host.dp(3));
            waveformContainer.addView(waveform, new FrameLayout.LayoutParams(-1, -1));

            title.setTextColor(host.primaryText);
            title.setEllipsize(TextUtils.TruncateAt.END);
            duration.setTextColor(host.secondaryText);
            marker.setBackgroundColor(host.yellow);
            configureButton(actions);
            configureButton(play);
            actions.setText("⋯");
            host.uiFactory.applyPlainIconStyle(actions);
            host.uiFactory.applyPrimaryButtonStyle(play);

            View.OnClickListener openOrPlay = view -> {
                Track track = boundTrack;
                TrackTapController.handle(host, track, cover);
            };
            card.setOnClickListener(openOrPlay);
            cover.setOnClickListener(openOrPlay);
            actions.setOnClickListener(view -> {
                if (boundTrack != null) {
                    host.overlayController.openSongActions(boundTrack);
                }
            });
            play.setOnClickListener(view -> {
                Track track = boundTrack;
                if (track == null) {
                    return;
                }
                if (host.isCurrent(track)) {
                    host.playbackQueueController.toggleOrStart();
                } else {
                    host.playbackQueueController.playTrack(track, true);
                }
            });
        }

        void bind(Track track) {
            boundTrack = track;
            title.setText(track.title);
            duration.setText(host.formatTrackDuration(track));
            waveform.setTrackKey(track.trackId);
            updatePlayback();
            host.artworkUi.loadUnregisteredCover(
                    cover, track, host.purpleSoft, CoverLoader.THUMB_SIZE);
        }

        void bindPayload(Track track, int payload) {
            boundTrack = track;
            if ((payload & PAYLOAD_METADATA) != 0) {
                title.setText(track.title);
                duration.setText(host.formatTrackDuration(track));
            }
            if ((payload & PAYLOAD_ARTWORK) != 0) {
                host.artworkUi.loadUnregisteredCover(
                        cover, track, host.purpleSoft, CoverLoader.THUMB_SIZE);
            }
            if ((payload & PAYLOAD_PLAYBACK) != 0) {
                updatePlayback();
            }
            if ((payload & PAYLOAD_POSITION) != 0) {
                updatePosition();
            }
        }

        void updatePlayback() {
            Track track = boundTrack;
            boolean current = track != null && track.trackId.equals(currentTrackId);
            marker.setVisibility(current ? View.VISIBLE : View.INVISIBLE);
            SongRowStateRegistry.applyPlayState(play, current && playing);
            waveform.setState(current ? host.purple : host.purpleSoft,
                    host.yellow, current && playing);
            if (current) {
                updatePosition();
            } else {
                waveform.setProgress(0L, 0L);
            }
            cover.updatePlaybackState();
        }

        void pauseDetachedAnimations() {
            waveform.setState(host.purpleSoft, host.yellow, false);
        }

        void recycle() {
            boundTrack = null;
            waveform.setState(host.purpleSoft, host.yellow, false);
            waveform.setProgress(0L, 0L);
            host.artworkUi.clearCover(cover, host.purpleSoft);
        }

        private void updatePosition() {
            waveform.setProgress(playbackPositionMs, playbackDurationMs);
        }

        private void configureButton(Button button) {
            button.setAllCaps(false);
            button.setStateListAnimator(null);
            button.setElevation(0.0f);
            button.setMinWidth(0);
            button.setMinHeight(0);
            button.setPadding(0, 0, 0, 0);
        }
    }
}
