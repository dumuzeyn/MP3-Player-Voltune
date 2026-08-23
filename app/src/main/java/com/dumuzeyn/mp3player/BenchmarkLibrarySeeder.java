package com.dumuzeyn.mp3player;

import android.content.Context;
import android.net.Uri;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Generates synthetic metadata only in the non-distributable benchmark build. */
final class BenchmarkLibrarySeeder {
    static final String EXTRA_TRACK_COUNT = "voltuneBenchmarkTrackCount";
    private static final int MAX_TRACKS = 10000;

    private BenchmarkLibrarySeeder() {
    }

    static void seedIfRequested(Context context, int requestedCount) {
        if (!"benchmark".equals(BuildConfig.BUILD_TYPE) || requestedCount <= 0) {
            return;
        }
        int count = Math.min(MAX_TRACKS, requestedCount);
        if (TrackStore.load(context).size() == count) {
            return;
        }
        ArrayList<Track> tracks = new ArrayList<>(count);
        String playableUri = createTestTone(context);
        for (int index = 0; index < count; index++) {
            String uri = index == 0 && !playableUri.isEmpty()
                    ? playableUri : "content://voltune.benchmark/track/" + index;
            String title = String.format(java.util.Locale.ROOT,
                    "Benchmark song %05d", index);
            if (index == 0 && !playableUri.isEmpty()) {
                tracks.add(new Track("benchmark-playable-track", uri, title,
                        "Benchmark artist 0", "Benchmark album 0", "Benchmark genre 0",
                        8000, -1L, 0L, "benchmark"));
            } else {
                tracks.add(new Track(uri, title,
                        "Benchmark artist " + (index % 200),
                        "Benchmark album " + (index % 500),
                        "Benchmark genre " + (index % 20), 180000 + (index % 120000)));
            }
        }
        TrackStore.save(context, tracks);
    }

    private static String createTestTone(Context context) {
        File file = new File(context.getFilesDir(), "benchmark-tone.wav");
        int sampleRate = 8000;
        int sampleCount = sampleRate * 8;
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
            output.writeBytes("RIFF");
            writeLeInt(output, 36 + sampleCount * 2);
            output.writeBytes("WAVEfmt ");
            writeLeInt(output, 16);
            writeLeShort(output, 1);
            writeLeShort(output, 1);
            writeLeInt(output, sampleRate);
            writeLeInt(output, sampleRate * 2);
            writeLeShort(output, 2);
            writeLeShort(output, 16);
            output.writeBytes("data");
            writeLeInt(output, sampleCount * 2);
            for (int sample = 0; sample < sampleCount; sample++) {
                double phase = 2.0 * Math.PI * 440.0 * sample / sampleRate;
                writeLeShort(output, (short) (Math.sin(phase) * 5000));
            }
            return Uri.fromFile(file).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void writeLeInt(DataOutputStream output, int value) throws Exception {
        output.writeByte(value & 0xff);
        output.writeByte((value >>> 8) & 0xff);
        output.writeByte((value >>> 16) & 0xff);
        output.writeByte((value >>> 24) & 0xff);
    }

    private static void writeLeShort(DataOutputStream output, int value) throws Exception {
        output.writeByte(value & 0xff);
        output.writeByte((value >>> 8) & 0xff);
    }
}
