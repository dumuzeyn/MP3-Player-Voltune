package com.dumuzeyn.mp3player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Small private disk cache containing only decoded artwork thumbnails. */
final class ArtworkDiskCache {
    private static final long MAX_BYTES = 48L * 1024L * 1024L;

    private final File directory;

    ArtworkDiskCache(Context context) {
        directory = new File(context.getCacheDir(), "artwork-v1");
    }

    Bitmap read(String key) {
        File file = fileFor(key);
        if (!file.isFile()) {
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            file.delete();
            return null;
        }
        file.setLastModified(System.currentTimeMillis());
        return bitmap;
    }

    void write(String key, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (!directory.exists() && !directory.mkdirs()) {
            return;
        }
        File target = fileFor(key);
        File temporary = new File(directory, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                temporary.delete();
                return;
            }
            if (target.exists()) {
                target.delete();
            }
            if (!temporary.renameTo(target)) {
                temporary.delete();
            }
            trim();
        } catch (Exception ignored) {
            temporary.delete();
        }
    }

    private void trim() {
        File[] files = directory.listFiles(file -> file.isFile() && !file.getName().endsWith(".tmp"));
        if (files == null) {
            return;
        }
        long total = 0L;
        for (File file : files) {
            total += Math.max(0L, file.length());
        }
        if (total <= MAX_BYTES) {
            return;
        }
        java.util.Arrays.sort(files,
                (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (File file : files) {
            long length = Math.max(0L, file.length());
            if (file.delete()) {
                total -= length;
            }
            if (total <= MAX_BYTES) {
                return;
            }
        }
    }

    private File fileFor(String key) {
        return new File(directory, digest(key) + ".jpg");
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
