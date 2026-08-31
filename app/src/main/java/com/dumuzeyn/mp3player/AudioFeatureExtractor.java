package com.dumuzeyn.mp3player;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/** Decodes short representative ranges and never retains a full PCM track. */
final class AudioFeatureExtractor {
    private static final long SEGMENT_US = 10_000_000L;
    private final Context context;

    AudioFeatureExtractor(Context context) {
        this.context = context.getApplicationContext();
    }

    double[] analyze(Track track, YieldSignal shouldYield) throws Exception {
        Probe probe = probe(track);
        AudioFeatureAccumulator accumulator = new AudioFeatureAccumulator(probe.sampleRate);
        for (long startUs : representativeStarts(probe.durationUs)) {
            if (shouldYield.shouldYield()) {
                throw new AnalysisInterruptedException();
            }
            accumulator.beginSegment();
            decodeRange(track, startUs, startUs + SEGMENT_US, probe.sampleRate,
                    probe.channels, accumulator, shouldYield);
        }
        double[] result = accumulator.finish();
        if (result.length != TrackAudioProfile.FEATURE_COUNT) {
            throw new IllegalStateException("insufficient_audio");
        }
        return result;
    }

    private Probe probe(Track track) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null);
            MediaFormat format = selectAudioTrack(extractor);
            if (format == null) {
                throw new IllegalArgumentException("audio_track_missing");
            }
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            long duration = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : track.durationMs * 1000L;
            return new Probe(sampleRate, channels, Math.max(SEGMENT_US, duration));
        } finally {
            extractor.release();
        }
    }

    private void decodeRange(Track track, long startUs, long endUs, int sampleRate,
            int channels, AudioFeatureAccumulator accumulator, YieldSignal shouldYield)
            throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null);
            MediaFormat format = selectAudioTrack(extractor);
            if (format == null) {
                throw new IllegalArgumentException("audio_track_missing");
            }
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IllegalArgumentException("audio_mime_missing");
            }
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            decode(extractor, decoder, startUs, endUs, sampleRate, channels,
                    accumulator, shouldYield);
        } finally {
            release(decoder);
            extractor.release();
        }
    }

    private static void decode(MediaExtractor extractor, MediaCodec decoder, long startUs,
            long endUs, int sampleRate, int channels, AudioFeatureAccumulator accumulator,
            YieldSignal shouldYield) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        while (!outputDone) {
            if (shouldYield.shouldYield() || Thread.currentThread().isInterrupted()) {
                throw new AnalysisInterruptedException();
            }
            if (!inputDone) {
                int inputIndex = decoder.dequeueInputBuffer(10_000L);
                if (inputIndex >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(inputIndex);
                    long time = extractor.getSampleTime();
                    int size = input == null ? -1 : extractor.readSampleData(input, 0);
                    if (size < 0 || time < 0L || time > endUs) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, Math.max(0L, time),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, size, time, 0);
                        extractor.advance();
                    }
                }
            }
            int outputIndex = decoder.dequeueOutputBuffer(info, 10_000L);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat output = decoder.getOutputFormat();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                        && output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING);
                }
            } else if (outputIndex >= 0) {
                ByteBuffer output = decoder.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0 && info.presentationTimeUs >= startUs
                        && info.presentationTimeUs <= endUs) {
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    accumulator.addPcm(output.slice().order(ByteOrder.LITTLE_ENDIAN),
                            encoding, channels);
                }
                outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                decoder.releaseOutputBuffer(outputIndex, false);
            }
        }
    }

    static ArrayList<Long> representativeStarts(long durationUs) {
        long duration = Math.max(SEGMENT_US, durationUs);
        long last = Math.max(0L, duration - SEGMENT_US);
        long middle = Math.max(0L, duration / 2L - SEGMENT_US / 2L);
        ArrayList<Long> result = new ArrayList<>();
        long afterIntro = duration >= 30_000_000L ? 5_000_000L : 0L;
        addDistinct(result, Math.min(afterIntro, last));
        addDistinct(result, Math.min(middle, last));
        addDistinct(result, last);
        return result;
    }

    private static void addDistinct(ArrayList<Long> values, long value) {
        if (!values.contains(value)) {
            values.add(value);
        }
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

    private static void release(MediaCodec decoder) {
        if (decoder == null) {
            return;
        }
        try {
            decoder.stop();
        } catch (RuntimeException ignored) {
        }
        try {
            decoder.release();
        } catch (RuntimeException ignored) {
        }
    }

    static final class AnalysisInterruptedException extends Exception {
    }

    interface YieldSignal {
        boolean shouldYield();
    }

    private static final class Probe {
        final int sampleRate;
        final int channels;
        final long durationUs;

        Probe(int sampleRate, int channels, long durationUs) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.durationUs = durationUs;
        }
    }
}
