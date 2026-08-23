package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class EmbeddedLyricsReaderTest {
    @Test
    public void readsUtf8UsltFrame() {
        byte[] lyrics = "Local lyrics".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[5 + lyrics.length];
        payload[0] = 3;
        payload[1] = 'e';
        payload[2] = 'n';
        payload[3] = 'g';
        payload[4] = 0;
        System.arraycopy(lyrics, 0, payload, 5, lyrics.length);
        byte[] frame = new byte[10 + payload.length];
        System.arraycopy("USLT".getBytes(StandardCharsets.ISO_8859_1), 0, frame, 0, 4);
        writeBigEndian(frame, 4, payload.length);
        System.arraycopy(payload, 0, frame, 10, payload.length);
        byte[] tag = new byte[10 + frame.length];
        tag[0] = 'I';
        tag[1] = 'D';
        tag[2] = '3';
        tag[3] = 3;
        writeSynchsafe(tag, 6, frame.length);
        System.arraycopy(frame, 0, tag, 10, frame.length);

        assertEquals("Local lyrics", EmbeddedLyricsReader.parse(tag));
    }

    @Test
    public void malformedOrOversizedDataReturnsEmpty() {
        assertTrue(EmbeddedLyricsReader.parse(new byte[]{1, 2, 3}).isEmpty());
    }

    private static void writeBigEndian(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static void writeSynchsafe(byte[] target, int offset, int value) {
        target[offset] = (byte) ((value >>> 21) & 0x7f);
        target[offset + 1] = (byte) ((value >>> 14) & 0x7f);
        target[offset + 2] = (byte) ((value >>> 7) & 0x7f);
        target[offset + 3] = (byte) (value & 0x7f);
    }
}
