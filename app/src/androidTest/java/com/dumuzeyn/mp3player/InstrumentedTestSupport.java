package com.dumuzeyn.mp3player;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class InstrumentedTestSupport {
    interface Condition {
        boolean isSatisfied();
    }

    private InstrumentedTestSupport() {
    }

    static File createTestWave(Context context, int durationSeconds) throws Exception {
        return createTestWave(context, "instrumented-playback.wav", durationSeconds);
    }

    static File createTestWave(Context context, String fileName, int durationSeconds)
            throws Exception {
        int sampleRate = 8000;
        int channels = 1;
        int bitsPerSample = 16;
        int sampleCount = sampleRate * durationSeconds;
        int dataSize = sampleCount * channels * bitsPerSample / 8;
        File output = new File(context.getCacheDir(), fileName);
        try (FileOutputStream stream = new FileOutputStream(output, false)) {
            stream.write(new byte[]{'R', 'I', 'F', 'F'});
            writeLittleEndian(stream, 36 + dataSize, 4);
            stream.write(new byte[]{'W', 'A', 'V', 'E'});
            stream.write(new byte[]{'f', 'm', 't', ' '});
            writeLittleEndian(stream, 16, 4);
            writeLittleEndian(stream, 1, 2);
            writeLittleEndian(stream, channels, 2);
            writeLittleEndian(stream, sampleRate, 4);
            writeLittleEndian(stream, sampleRate * channels * bitsPerSample / 8, 4);
            writeLittleEndian(stream, channels * bitsPerSample / 8, 2);
            writeLittleEndian(stream, bitsPerSample, 2);
            stream.write(new byte[]{'d', 'a', 't', 'a'});
            writeLittleEndian(stream, dataSize, 4);
            for (int index = 0; index < sampleCount; index++) {
                double phase = 2.0 * Math.PI * 440.0 * index / sampleRate;
                short sample = (short) (Math.sin(phase) * 1200.0);
                writeLittleEndian(stream, sample, 2);
            }
        }
        return output;
    }

    static void waitFor(String message, long timeoutMs, Condition condition) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.isSatisfied()) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(message);
    }

    static void finishActivity(Instrumentation instrumentation, Activity activity) {
        finishActivity(instrumentation, activity, false);
    }

    static void finishAndRemoveTask(Instrumentation instrumentation, Activity activity) {
        finishActivity(instrumentation, activity, true);
    }

    private static void finishActivity(Instrumentation instrumentation, Activity activity,
            boolean removeTask) {
        if (activity == null || activity.isDestroyed()) {
            return;
        }
        instrumentation.runOnMainSync(() -> {
            if (removeTask) {
                activity.finishAndRemoveTask();
            } else {
                activity.finish();
            }
        });
        waitFor("Activity did not start finishing", 5000L, activity::isFinishing);
        instrumentation.waitForIdleSync();
        SystemClock.sleep(250L);
    }

    static void runShellCommand(Instrumentation instrumentation, String command) throws Exception {
        ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
        if (descriptor == null) {
            return;
        }
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[256];
            while (stream.read(buffer) >= 0) {
                // Reading until EOF waits for the shell command to complete.
            }
        }
    }

    private static void writeLittleEndian(FileOutputStream stream, int value, int byteCount)
            throws Exception {
        for (int index = 0; index < byteCount; index++) {
            stream.write((value >> (8 * index)) & 0xff);
        }
    }
}
