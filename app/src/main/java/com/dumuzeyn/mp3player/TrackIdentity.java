package com.dumuzeyn.mp3player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/** Creates opaque identities that never expose a content URI. */
public final class TrackIdentity {
    private TrackIdentity() {
    }

    public static String create() {
        return "track-" + UUID.randomUUID();
    }

    public static String fromLegacyUri(String uri) {
        String value = uri == null ? "" : uri;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("legacy-");
            for (int index = 0; index < 16; index++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return "legacy-" + Integer.toHexString(value.hashCode());
        }
    }
}
