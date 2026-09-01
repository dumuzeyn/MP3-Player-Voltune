package com.dumuzeyn.mp3player;

import android.net.Uri;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** A persisted SAF tree that owns imported library tracks. */
public final class LibrarySource {
    final String sourceId;
    final String treeUri;
    final String displayName;
    final long revision;

    LibrarySource(String sourceId, String treeUri, String displayName, long revision) {
        this.sourceId = sourceId;
        this.treeUri = treeUri;
        this.displayName = displayName;
        this.revision = revision;
    }

    Uri asUri() {
        return Uri.parse(treeUri);
    }

    static String idFor(Uri treeUri) {
        String value = treeUri == null ? "" : treeUri.normalizeScheme().toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("saf:");
            for (byte item : bytes) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (Exception error) {
            return "saf:" + Integer.toHexString(value.hashCode());
        }
    }
}
