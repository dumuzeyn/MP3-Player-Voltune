package com.dumuzeyn.mp3player;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Accepts only bounded, decodable raster images from a read-only content URI. */
final class BackgroundMediaValidator {
    private static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_PIXELS = 80L * 1024L * 1024L;

    private BackgroundMediaValidator() {
    }

    static Result validate(Context context, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) {
            return Result.invalid();
        }
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || !mime.toLowerCase(Locale.ROOT).startsWith("image/")
                || "image/svg+xml".equalsIgnoreCase(mime)) {
            return Result.invalid();
        }
        long size = querySize(context, uri);
        if (size > MAX_FILE_BYTES) {
            return Result.invalid();
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            byte[] header = new byte[32];
            int count = input == null ? -1 : input.read(header);
            if (count < 10 || !isSupportedRaster(header, count)) {
                return Result.invalid();
            }
        } catch (IOException | SecurityException error) {
            return Result.invalid();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException | SecurityException error) {
            return Result.invalid();
        }
        long pixels = (long) bounds.outWidth * (long) bounds.outHeight;
        boolean valid = bounds.outWidth > 0 && bounds.outHeight > 0 && pixels <= MAX_PIXELS;
        boolean heavy = valid && (("image/gif".equalsIgnoreCase(mime) && size > 8L * 1024L * 1024L)
                || pixels > 24L * 1024L * 1024L);
        return valid ? Result.valid(heavy) : Result.invalid();
    }

    private static long querySize(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return Math.max(0L, cursor.getLong(0));
            }
        } catch (RuntimeException ignored) {
        }
        return 0L;
    }

    private static boolean isSupportedRaster(byte[] data, int count) {
        if (unsigned(data[0]) == 0xff && unsigned(data[1]) == 0xd8 && unsigned(data[2]) == 0xff) {
            return true;
        }
        if (count >= 8 && unsigned(data[0]) == 0x89 && data[1] == 'P' && data[2] == 'N'
                && data[3] == 'G' && unsigned(data[4]) == 0x0d && unsigned(data[5]) == 0x0a
                && unsigned(data[6]) == 0x1a && unsigned(data[7]) == 0x0a) {
            return true;
        }
        if (count >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            return true;
        }
        if (count >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F'
                && data[3] == 'F' && data[8] == 'W' && data[9] == 'E'
                && data[10] == 'B' && data[11] == 'P') {
            return true;
        }
        if (data[0] == 'B' && data[1] == 'M') {
            return true;
        }
        if (count >= 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') {
            String brand = new String(data, 8, Math.min(count - 8, 16),
                    java.nio.charset.StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            return brand.contains("heic") || brand.contains("heif") || brand.contains("mif1")
                    || brand.contains("avif") || brand.contains("avis");
        }
        return false;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    static final class Result {
        final boolean valid;
        final boolean heavy;

        private Result(boolean valid, boolean heavy) {
            this.valid = valid;
            this.heavy = heavy;
        }

        static Result valid(boolean heavy) {
            return new Result(true, heavy);
        }

        static Result invalid() {
            return new Result(false, false);
        }
    }
}
