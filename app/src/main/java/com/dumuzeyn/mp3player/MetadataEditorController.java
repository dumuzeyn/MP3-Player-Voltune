package com.dumuzeyn.mp3player;

import android.net.Uri;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Safe library metadata editor; source audio bytes are never modified. */
final class MetadataEditorController implements AutoCloseable {
    private final MainActivityCore host;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    MetadataEditorController(MainActivityCore host) {
        this.host = host;
    }

    void open(Track track) {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        LinearLayout header = host.uiFactory.row();
        header.addView(host.uiFactory.text(host.tr("Metadata", "Метаданные"), 21, true),
                new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        Button close = host.uiFactory.icon("×");
        close.setContentDescription(host.tr("Close", "Закрыть"));
        close.setOnClickListener(view -> close(shade));
        header.addView(close, host.uiFactory.square(50));
        panel.addView(header);

        ScrollView scroll = new ScrollView(host);
        LinearLayout fields = new LinearLayout(host);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(host.uiFactory.text(host.tr(
                "Edits apply to the Voltune library. The source file stays unchanged.",
                "Изменения применяются к библиотеке Voltune. Исходный файл не изменяется."),
                14, false));
        EditText title = field(fields, host.tr("Title", "Название"), track.title, false);
        EditText artist = field(fields, host.tr("Artist", "Исполнитель"), track.artist, false);
        EditText album = field(fields, host.tr("Album", "Альбом"), track.album, false);
        EditText albumArtist = field(fields, host.tr("Album artist", "Исполнитель альбома"),
                track.albumArtist, false);
        EditText genre = field(fields, host.tr("Genre", "Жанр"), track.genre, false);
        EditText year = field(fields, host.tr("Year", "Год"), number(track.year), true);
        EditText trackNumber = field(fields, host.tr("Track number", "Номер трека"),
                number(track.trackNumber), true);
        EditText discNumber = field(fields, host.tr("Disc number", "Номер диска"),
                number(track.discNumber), true);
        fields.addView(host.uiFactory.text(readOnlyDetails(track), 14, false));
        Button save = host.uiFactory.button(host.tr("Save in library", "Сохранить в библиотеке"));
        host.uiFactory.applyPrimaryButtonStyle(save);
        save.setOnClickListener(view -> {
            Track updated = track.withMetadata(required(title, track.title),
                    required(artist, track.artist), required(album, track.album),
                    required(albumArtist, artist.getText().toString()),
                    required(genre, track.genre), MetadataValidator.year(year.getText().toString()),
                    MetadataValidator.trackNumber(trackNumber.getText().toString()),
                    MetadataValidator.trackNumber(discNumber.getText().toString()));
            apply(updated);
            close(shade);
        });
        fields.addView(save, new LinearLayout.LayoutParams(-1, host.dp(54)));
        scroll.addView(fields);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        shade.addView(panel, host.bottomParams());
        host.overlayHost.addView(shade);
    }

    void applyBatch(List<Track> tracks, String artist, String album, String genre) {
        ArrayList<Track> updates = new ArrayList<>();
        for (Track track : new ArrayList<>(tracks)) {
            updates.add(track.withMetadata(track.title,
                    emptyKeeps(artist, track.artist), emptyKeeps(album, track.album),
                    track.albumArtist, emptyKeeps(genre, track.genre), track.year,
                    track.trackNumber, track.discNumber));
        }
        applyMany(updates);
    }

    void openBatchSelection() {
        host.overlayController.openSelection(host.tr(
                "Select tracks to edit", "Выберите треки для изменения"),
                new HashSet<>(), selected -> {
                    ArrayList<Track> tracks = new ArrayList<>();
                    for (String uri : selected) {
                        Track track = host.findTrack(uri);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                    if (!tracks.isEmpty()) {
                        openBatchEditor(tracks);
                    }
                });
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }

    private void apply(Track updated) {
        applyMany(java.util.Collections.singletonList(updated));
    }

    private void applyMany(List<Track> updates) {
        ArrayList<Track> persisted = new ArrayList<>();
        for (Track updated : updates) {
            if (replaceInMemory(updated)) {
                persisted.add(updated);
            }
        }
        if (persisted.isEmpty()) {
            return;
        }
        host.libraryRepository.reindex();
            host.librarySnapshotApplier.rebuildDerivedAndRender();
        try {
            executor.execute(() -> TrackStore.updateMetadata(
                    host.getApplicationContext(), persisted));
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    private boolean replaceInMemory(Track updated) {
        int index = indexOf(updated.trackId);
        if (index < 0) {
            return false;
        }
        host.libraryState.tracks.set(index, updated);
        for (int queueIndex = 0; queueIndex < host.playbackUiState.queue.size(); queueIndex++) {
            if (updated.trackId.equals(host.playbackUiState.queue.get(queueIndex).trackId)) {
                host.playbackUiState.queue.set(queueIndex, updated);
            }
        }
        return true;
    }

    private void openBatchEditor(List<Track> tracks) {
        FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        LinearLayout header = host.uiFactory.row();
        header.addView(host.uiFactory.text(host.tr("Batch metadata", "Массовые метаданные"),
                20, true), new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        Button close = host.uiFactory.icon("×");
        close.setOnClickListener(view -> close(shade));
        header.addView(close, host.uiFactory.square(50));
        panel.addView(header);
        LinearLayout fields = new LinearLayout(host);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(host.uiFactory.text(host.tr(
                "Blank fields keep their current values. Source files stay unchanged.",
                "Пустые поля сохраняют текущие значения. Исходные файлы не изменяются."),
                14, false));
        EditText artist = field(fields, host.tr("Artist", "Исполнитель"), "", false);
        EditText album = field(fields, host.tr("Album", "Альбом"), "", false);
        EditText genre = field(fields, host.tr("Genre", "Жанр"), "", false);
        Button save = host.uiFactory.button(host.tr("Apply", "Применить"));
        host.uiFactory.applyPrimaryButtonStyle(save);
        save.setOnClickListener(view -> {
            applyBatch(tracks, artist.getText().toString(), album.getText().toString(),
                    genre.getText().toString());
            close(shade);
        });
        fields.addView(save, new LinearLayout.LayoutParams(-1, host.dp(54)));
        panel.addView(fields, new LinearLayout.LayoutParams(-1, -2));
        shade.addView(panel, host.bottomParams());
        host.overlayHost.addView(shade);
    }

    private int indexOf(String trackId) {
        for (int index = 0; index < host.libraryState.tracks.size(); index++) {
            if (trackId.equals(host.libraryState.tracks.get(index).trackId)) {
                return index;
            }
        }
        return -1;
    }

    private EditText field(LinearLayout parent, String hint, String value, boolean numeric) {
        EditText input = new EditText(host);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(host.fg);
        input.setHintTextColor(host.muted);
        input.setSingleLine(true);
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        parent.addView(input, new LinearLayout.LayoutParams(-1, host.dp(52)));
        return input;
    }

    private String readOnlyDetails(Track track) {
        String source;
        try {
            Uri uri = Uri.parse(track.uri);
            source = uri.getAuthority() == null ? host.tr("Local source", "Локальный источник")
                    : uri.getAuthority();
        } catch (RuntimeException error) {
            source = host.tr("Unavailable source", "Недоступный источник");
        }
        String format = extension(track.uri);
        return host.tr("Read only", "Только чтение") + ":\n"
                + host.tr("Duration", "Длительность") + ": " + host.formatTrackDuration(track)
                + "\n" + host.tr("Format", "Формат") + ": " + format
                + "\n" + host.tr("Size", "Размер") + ": " + size(track.fileSize)
                + "\n" + host.tr("Source", "Источник") + ": " + source
                + "\n" + host.tr("Artwork and bitrate depend on the source provider.",
                "Обложка и bitrate зависят от исходного provider.");
    }

    private static String required(EditText field, String fallback) {
        String value = MetadataValidator.cleanText(field.getText().toString());
        return value.isEmpty() ? fallback : value;
    }

    private static String emptyKeeps(String value, String fallback) {
        String cleaned = MetadataValidator.cleanText(value);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String number(int value) {
        return value <= 0 ? "" : String.valueOf(value);
    }

    private static String extension(String uri) {
        int dot = uri == null ? -1 : uri.lastIndexOf('.');
        return dot < 0 ? "Unknown" : uri.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static String size(long bytes) {
        return bytes < 0L ? "Unknown" : String.format(java.util.Locale.ROOT, "%.1f MB",
                bytes / 1024d / 1024d);
    }

    private void close(FrameLayout shade) {
        if (shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        host.playerUiController.updateMini();
    }
}
