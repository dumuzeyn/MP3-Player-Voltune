package com.dumuzeyn.mp3player

enum class SoundAnalysisState {
    NOT_ANALYZED,
    QUEUED,
    ANALYZING,
    ANALYZED,
    FAILED;

    companion object {
        @JvmStatic
        fun parse(value: String?): SoundAnalysisState =
            entries.firstOrNull { it.name == value } ?: NOT_ANALYZED
    }
}
