package com.dumuzeyn.mp3player;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Bounded, read-only ID3 USLT reader for embedded offline lyrics. */
final class EmbeddedLyricsReader {
    private static final int MAX_TAG_BYTES = 1024 * 1024;

    String read(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return "";
            }
            byte[] header = readExactly(input, 10);
            if (header.length != 10 || header[0] != 'I' || header[1] != 'D'
                    || header[2] != '3') {
                return "";
            }
            int size = synchsafe(header, 6);
            if (size <= 0 || size > MAX_TAG_BYTES) {
                return "";
            }
            byte[] tag = readExactly(input, size);
            return parseFrames(tag, header[3]);
        } catch (Exception error) {
            VoltuneLog.failure("embedded_lyrics_read_failed", error);
            return "";
        }
    }

    static String parse(byte[] id3) {
        if (id3 == null || id3.length < 10 || id3[0] != 'I' || id3[1] != 'D'
                || id3[2] != '3') {
            return "";
        }
        int size = Math.min(synchsafe(id3, 6), id3.length - 10);
        byte[] tag = new byte[Math.max(0, size)];
        System.arraycopy(id3, 10, tag, 0, tag.length);
        return parseFrames(tag, id3[3]);
    }

    private static String parseFrames(byte[] tag, int version) {
        int offset = 0;
        while (offset + 10 <= tag.length) {
            String id = new String(tag, offset, 4, StandardCharsets.ISO_8859_1);
            if (!id.matches("[A-Z0-9]{4}")) {
                break;
            }
            int size = version >= 4 ? synchsafe(tag, offset + 4)
                    : bigEndian(tag, offset + 4);
            int payload = offset + 10;
            if (size < 0 || payload + size > tag.length) {
                break;
            }
            if ("USLT".equals(id)) {
                return decodeUslt(tag, payload, size);
            }
            offset = payload + size;
        }
        return "";
    }

    private static String decodeUslt(byte[] bytes, int offset, int size) {
        if (size < 5) {
            return "";
        }
        int encoding = bytes[offset] & 0xff;
        int textStart = offset + 4;
        int end = offset + size;
        int terminatorWidth = encoding == 1 || encoding == 2 ? 2 : 1;
        while (textStart + terminatorWidth <= end) {
            if (bytes[textStart] == 0
                    && (terminatorWidth == 1 || bytes[textStart + 1] == 0)) {
                textStart += terminatorWidth;
                break;
            }
            textStart += terminatorWidth;
        }
        if (textStart >= end) {
            return "";
        }
        Charset charset = charset(encoding);
        String value = new String(bytes, textStart, end - textStart, charset)
                .replace('\u0000', ' ').trim();
        return value.length() > MAX_TAG_BYTES ? value.substring(0, MAX_TAG_BYTES) : value;
    }

    private static Charset charset(int encoding) {
        if (encoding == 1) {
            return StandardCharsets.UTF_16;
        }
        if (encoding == 2) {
            return StandardCharsets.UTF_16BE;
        }
        if (encoding == 3) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.ISO_8859_1;
    }

    private static int synchsafe(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            return 0;
        }
        return ((bytes[offset] & 0x7f) << 21) | ((bytes[offset + 1] & 0x7f) << 14)
                | ((bytes[offset + 2] & 0x7f) << 7) | (bytes[offset + 3] & 0x7f);
    }

    private static int bigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
    }

    private static byte[] readExactly(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        while (output.size() < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
            if (count < 0) {
                break;
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
