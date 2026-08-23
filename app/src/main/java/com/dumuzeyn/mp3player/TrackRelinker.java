package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;

/** Matches a newly discovered file to a stable library record using multiple signals. */
final class TrackRelinker {
    private TrackRelinker() {
    }

    static List<Track> candidates(List<Track> library, Track discovered) {
        ArrayList<Track> result = new ArrayList<>();
        int bestScore = 0;
        for (Track existing : library) {
            int score = score(existing, discovered);
            if (score < 8) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                result.clear();
            }
            if (score == bestScore) {
                result.add(existing);
            }
        }
        return result;
    }

    static int score(Track left, Track right) {
        int score = 0;
        if (!left.fingerprint.isEmpty() && left.fingerprint.equals(right.fingerprint)) {
            score += 8;
        }
        if (left.fileSize > 0L && left.fileSize == right.fileSize) {
            score += 3;
        }
        if (left.durationMs > 0 && Math.abs(left.durationMs - right.durationMs) <= 1000) {
            score += 2;
        }
        if (same(left.title, right.title)) {
            score += 2;
        }
        if (same(left.artist, right.artist)) {
            score++;
        }
        if (same(left.album, right.album)) {
            score++;
        }
        if (left.lastModified > 0L && left.lastModified == right.lastModified) {
            score++;
        }
        return score;
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
