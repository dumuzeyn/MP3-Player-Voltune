package com.dumuzeyn.mp3player;

import java.util.Locale;

final class TrackAudioProfile {
    static final int ANALYSIS_VERSION = 2;
    static final int FEATURE_COUNT = 19;
    static final int BPM = 0;
    static final int ENERGY = 1;
    static final int LOUDNESS = 2;
    static final int DYNAMIC_RANGE = 3;
    static final int CENTROID = 4;
    static final int BANDWIDTH = 5;
    static final int ROLLOFF = 6;
    static final int ZERO_CROSSING = 7;
    static final int BASS = 8;
    static final int TREBLE = 9;
    static final int RHYTHM = 10;
    static final int CONTRAST = 11;
    static final int TEMPO_CONFIDENCE = 12;
    static final int TIMBRE_START = 13;

    final String trackId;
    final int analysisVersion;
    final long fileSize;
    final long lastModified;
    final String fingerprint;
    final SoundAnalysisState state;
    final double[] features;
    final String groupId;
    final String error;
    final long updatedAt;

    TrackAudioProfile(String trackId, int analysisVersion, long fileSize, long lastModified,
            String fingerprint, SoundAnalysisState state, double[] features, String groupId,
            String error, long updatedAt) {
        this.trackId = trackId == null ? "" : trackId;
        this.analysisVersion = analysisVersion;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.state = state == null ? SoundAnalysisState.NOT_ANALYZED : state;
        this.features = sanitize(features);
        this.groupId = groupId == null ? "" : groupId;
        this.error = error == null ? "" : error;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    static TrackAudioProfile pending(Track track, SoundAnalysisState state) {
        return new TrackAudioProfile(track.trackId, ANALYSIS_VERSION, track.fileSize,
                track.lastModified, track.fingerprint, state, new double[0], "", "",
                System.currentTimeMillis());
    }

    static TrackAudioProfile analyzed(Track track, double[] features) {
        return new TrackAudioProfile(track.trackId, ANALYSIS_VERSION, track.fileSize,
                track.lastModified, track.fingerprint, SoundAnalysisState.ANALYZED,
                features, "", "", System.currentTimeMillis());
    }

    boolean matches(Track track) {
        return track != null && analysisVersion == ANALYSIS_VERSION
                && fileSize == track.fileSize && lastModified == track.lastModified
                && fingerprint.equals(track.fingerprint == null ? "" : track.fingerprint);
    }

    boolean usable() {
        return state == SoundAnalysisState.ANALYZED && features.length == FEATURE_COUNT;
    }

    TrackAudioProfile withGroup(String value) {
        return new TrackAudioProfile(trackId, analysisVersion, fileSize, lastModified,
                fingerprint, state, features, value, error, updatedAt);
    }

    String encodeFeatures() {
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < features.length; index++) {
            if (index > 0) {
                encoded.append(',');
            }
            encoded.append(String.format(Locale.ROOT, "%.8f", features[index]));
        }
        return encoded.toString();
    }

    static double[] decodeFeatures(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return new double[0];
        }
        String[] parts = encoded.split(",", -1);
        double[] values = new double[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                values[index] = Double.parseDouble(parts[index]);
            }
            return sanitize(values);
        } catch (NumberFormatException error) {
            return new double[0];
        }
    }

    private static double[] sanitize(double[] source) {
        if (source == null) {
            return new double[0];
        }
        double[] copy = source.clone();
        for (int index = 0; index < copy.length; index++) {
            if (!Double.isFinite(copy[index])) {
                copy[index] = 0.0d;
            }
        }
        return copy;
    }
}
