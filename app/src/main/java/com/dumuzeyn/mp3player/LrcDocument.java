package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LrcDocument {
    final List<LrcLine> lines;
    final String plainText;
    final boolean synchronizedLyrics;

    LrcDocument(List<LrcLine> lines, String plainText, boolean synchronizedLyrics) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.plainText = plainText == null ? "" : plainText;
        this.synchronizedLyrics = synchronizedLyrics;
    }

    int lineAt(long positionMs) {
        int low = 0;
        int high = lines.size() - 1;
        int result = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMs <= positionMs) {
                result = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return result;
    }
}
