package com.dumuzeyn.mp3player;

enum SoundAnalysisState {
    NOT_ANALYZED,
    QUEUED,
    ANALYZING,
    ANALYZED,
    FAILED;

    static SoundAnalysisState parse(String value) {
        try {
            return valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException error) {
            return NOT_ANALYZED;
        }
    }
}
