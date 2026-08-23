package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Performs bounded EBU R128-style analysis and caches content-versioned results. */
final class TrackLoudnessNormalizer {
    interface ProgressListener {
        void onProgress(int completed, int total, int errors, boolean finished,
                boolean cancelled);
    }

    static final int ALGORITHM_VERSION = 3;
    static final String PREFS = "track_loudness_cache";
    static final String REDUCE_ONLY = "reduce_only";
    static final String TARGET_LUFS = "target_lufs";
    private static final String ANALYSIS_PROFILE = "kweight-400ms-100ms-gates70-10-peak4";
    private static final String DEBUG_TAG = "VoltuneDebug";
    private static final String RESULT_PREFIX = "r128_result_";
    private static final String ERROR_PREFIX = "r128_error_";
    private static final long MAX_ANALYSIS_US = 15L * 60L * 1_000_000L;

    private final Context context;
    private final SharedPreferences cache;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<String> pending = Collections.synchronizedSet(new HashSet<>());
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean batchRunning;

    TrackLoudnessNormalizer(Context context) {
        this.context = context.getApplicationContext();
        this.cache = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    float cachedGainDb(Track track) {
        if (!enabled() || track == null) {
            return 0.0f;
        }
        LoudnessAnalysisResult result = cachedResult(track);
        return result == null ? 0.0f : LoudnessGainPolicy.gainDb(result.integratedLufs,
                result.peakDbfs, targetLufs(), reduceOnly());
    }

    void prefetch(List<Track> queue, int currentIndex) {
        if (!enabled() || queue == null || queue.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < Math.min(3, queue.size()); offset++) {
            prefetch(queue.get((Math.max(0, currentIndex) + offset) % queue.size()));
        }
    }

    void analyzeLibrary(List<Track> tracks, ProgressListener listener) {
        if (batchRunning) {
            notifyProgress(listener, 0, tracks == null ? 0 : tracks.size(),
                    errorCount(tracks),
                    false, false);
            return;
        }
        ArrayList<Track> source = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
        cancelRequested.set(false);
        batchRunning = true;
        executor.execute(() -> {
            int completed = 0;
            int errors = 0;
            for (Track track : source) {
                if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (cachedResult(track) == null && !analyzeAndCache(track)) {
                    errors++;
                }
                completed++;
                notifyProgress(listener, completed, source.size(), errors, false, false);
            }
            boolean cancelled = cancelRequested.get() || completed < source.size();
            batchRunning = false;
            notifyProgress(listener, completed, source.size(), errors, true, cancelled);
        });
    }

    void cancelAnalysis() {
        cancelRequested.set(true);
    }

    void clearCache() {
        cancelAnalysis();
        cache.edit().clear().commit();
    }

    int analyzedCount(List<Track> tracks) {
        int count = 0;
        if (tracks != null) {
            for (Track track : tracks) {
                if (cachedResult(track) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    int errorCount(List<Track> tracks) {
        Set<String> failed = failedTrackIds();
        int count = 0;
        if (tracks != null) {
            for (Track track : tracks) {
                if (failed.contains(track.trackId)) {
                    count++;
                }
            }
        }
        return count;
    }

    Set<String> failedTrackIds() {
        HashSet<String> ids = new HashSet<>();
        for (String key : cache.getAll().keySet()) {
            if (key.startsWith(ERROR_PREFIX)) {
                ids.add(key.substring(ERROR_PREFIX.length()));
            }
        }
        return ids;
    }

    boolean isBatchRunning() {
        return batchRunning;
    }

    void release() {
        cancelAnalysis();
        executor.shutdownNow();
        pending.clear();
    }

    static String cacheKeyFor(Track track) {
        return cacheKeyFor(track, ALGORITHM_VERSION);
    }

    static String cacheKeyFor(Track track, int algorithmVersion) {
        if (track == null) {
            return "none";
        }
        String identity = track.trackId + '|' + track.fileSize + '|' + track.lastModified
                + '|' + algorithmVersion + '|' + ANALYSIS_PROFILE;
        return hash(identity);
    }

    private void prefetch(Track track) {
        if (track == null || cachedResult(track) != null || !pending.add(track.trackId)) {
            return;
        }
        executor.execute(() -> {
            try {
                analyzeAndCache(track);
            } finally {
                pending.remove(track.trackId);
            }
        });
    }

    private boolean analyzeAndCache(Track track) {
        if (track == null || cancelRequested.get()) {
            return false;
        }
        try {
            LoudnessAnalysisResult result = analyze(track);
            if (result == null) {
                recordError(track, "no_audio_result");
                return false;
            }
            String encoded = result.integratedLufs + "," + result.peakDbfs;
            cache.edit()
                    .putString(RESULT_PREFIX + cacheKeyFor(track), encoded)
                    .remove(ERROR_PREFIX + track.trackId)
                    .apply();
            Log.i(DEBUG_TAG, "r128_analyzed lufs=" + result.integratedLufs
                    + " peakDbfs=" + result.peakDbfs + " trackId="
                    + PlaybackEventLogger.opaqueMediaId(track.trackId));
            return true;
        } catch (Exception error) {
            recordError(track, error.getClass().getSimpleName());
            Log.w(DEBUG_TAG, "r128_analysis_failed category="
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    private LoudnessAnalysisResult analyze(Track track) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null);
            MediaFormat format = selectAudioTrack(extractor);
            if (format == null) {
                return null;
            }
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                return null;
            }
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            return decode(extractor, decoder, sampleRate, channels);
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (RuntimeException ignored) {
                }
                try {
                    decoder.release();
                } catch (RuntimeException ignored) {
                }
            }
            extractor.release();
        }
    }

    private LoudnessAnalysisResult decode(MediaExtractor extractor, MediaCodec decoder,
            int sampleRate, int channels) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        R128LoudnessMeter meter = new R128LoudnessMeter(sampleRate, channels);
        boolean inputDone = false;
        boolean outputDone = false;
        int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
        while (!outputDone && !cancelRequested.get() && !Thread.currentThread().isInterrupted()) {
            if (!inputDone) {
                int inputIndex = decoder.dequeueInputBuffer(10_000L);
                if (inputIndex >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(inputIndex);
                    int size = input == null ? -1 : extractor.readSampleData(input, 0);
                    long sampleTime = extractor.getSampleTime();
                    if (size < 0 || sampleTime > MAX_ANALYSIS_US) {
                        decoder.queueInputBuffer(inputIndex, 0, 0,
                                Math.max(0L, sampleTime), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, size, sampleTime, 0);
                        extractor.advance();
                    }
                }
            }
            int outputIndex = decoder.dequeueOutputBuffer(info, 10_000L);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat output = decoder.getOutputFormat();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                        && output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    pcmEncoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING);
                }
            } else if (outputIndex >= 0) {
                ByteBuffer output = decoder.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0) {
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    meter.add(output.slice().order(ByteOrder.LITTLE_ENDIAN), pcmEncoding);
                }
                outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                decoder.releaseOutputBuffer(outputIndex, false);
            }
        }
        return cancelRequested.get() ? null : meter.result();
    }

    private static MediaFormat selectAudioTrack(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                extractor.selectTrack(index);
                return format;
            }
        }
        return null;
    }

    private LoudnessAnalysisResult cachedResult(Track track) {
        String key = RESULT_PREFIX + cacheKeyFor(track);
        String encoded = cache.getString(key, "");
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            String[] values = encoded.split(",", -1);
            if (values.length != 2) {
                throw new IllegalArgumentException("invalid result");
            }
            float lufs = Float.parseFloat(values[0]);
            float peak = Float.parseFloat(values[1]);
            if (!Float.isFinite(lufs) || !Float.isFinite(peak)) {
                throw new IllegalArgumentException("non-finite result");
            }
            return new LoudnessAnalysisResult(lufs, peak);
        } catch (RuntimeException error) {
            cache.edit().remove(key).apply();
            return null;
        }
    }

    private void recordError(Track track, String category) {
        if (track != null && !cancelRequested.get()) {
            cache.edit().putString(ERROR_PREFIX + track.trackId,
                    category == null ? "unknown" : category).apply();
        }
    }

    private boolean enabled() {
        return context.getSharedPreferences(EqualizerController.PREFS, Context.MODE_PRIVATE)
                .getBoolean(VolumeLevelingController.ENABLED, false);
    }

    private boolean reduceOnly() {
        return context.getSharedPreferences(EqualizerController.PREFS, Context.MODE_PRIVATE)
                .getBoolean(REDUCE_ONLY, false);
    }

    private float targetLufs() {
        int target = context.getSharedPreferences(EqualizerController.PREFS,
                Context.MODE_PRIVATE).getInt(TARGET_LUFS,
                Math.round(LoudnessGainPolicy.DEFAULT_TARGET_LUFS));
        return Math.max(-24, Math.min(-10, target));
    }

    private void notifyProgress(ProgressListener listener, int completed, int total, int errors,
            boolean finished, boolean cancelled) {
        if (listener != null) {
            mainHandler.post(() -> listener.onProgress(completed, total, errors, finished,
                    cancelled));
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
