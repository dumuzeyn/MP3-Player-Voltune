package com.dumuzeyn.mp3player

import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

internal class TrackAudioProfile(
    trackId: String?,
    @JvmField val analysisVersion: Int,
    @JvmField val fileSize: Long,
    @JvmField val lastModified: Long,
    fingerprint: String?,
    state: SoundAnalysisState?,
    features: DoubleArray?,
    groupId: String?,
    error: String?,
    updatedAt: Long,
) {
    @JvmField val trackId: String = trackId.orEmpty()
    @JvmField val fingerprint: String = fingerprint.orEmpty()
    @JvmField val state: SoundAnalysisState = state ?: SoundAnalysisState.NOT_ANALYZED
    @JvmField val features: DoubleArray = sanitize(features)
    @JvmField val groupId: String = groupId.orEmpty()
    @JvmField val error: String = error.orEmpty()
    @JvmField val updatedAt: Long = max(0L, updatedAt)

    fun matches(track: Track?): Boolean =
        track != null && analysisVersion == ANALYSIS_VERSION &&
            fileSize == track.fileSize && lastModified == track.lastModified &&
            fingerprint == track.fingerprint.orEmpty()

    fun usable(): Boolean =
        state == SoundAnalysisState.ANALYZED && features.size == FEATURE_COUNT

    fun withGroup(value: String?): TrackAudioProfile = TrackAudioProfile(
        trackId,
        analysisVersion,
        fileSize,
        lastModified,
        fingerprint,
        state,
        features,
        value,
        error,
        updatedAt,
    )

    fun encodeFeatures(): String = features.joinToString(",") { value ->
        String.format(Locale.ROOT, "%.8f", value)
    }

    companion object {
        const val ANALYSIS_VERSION = 3
        const val FEATURE_COUNT = 19
        const val BPM = 0
        const val ENERGY = 1
        const val LOUDNESS = 2
        const val DYNAMIC_RANGE = 3
        const val CENTROID = 4
        const val BANDWIDTH = 5
        const val ROLLOFF = 6
        const val ZERO_CROSSING = 7
        const val BASS = 8
        const val TREBLE = 9
        const val RHYTHM = 10
        const val CONTRAST = 11
        const val TEMPO_CONFIDENCE = 12
        const val TIMBRE_START = 13

        @JvmStatic
        fun pending(track: Track, state: SoundAnalysisState): TrackAudioProfile =
            TrackAudioProfile(
                track.trackId,
                ANALYSIS_VERSION,
                track.fileSize,
                track.lastModified,
                track.fingerprint,
                state,
                DoubleArray(0),
                "",
                "",
                System.currentTimeMillis(),
            )

        @JvmStatic
        fun analyzed(track: Track, features: DoubleArray?): TrackAudioProfile =
            TrackAudioProfile(
                track.trackId,
                ANALYSIS_VERSION,
                track.fileSize,
                track.lastModified,
                track.fingerprint,
                SoundAnalysisState.ANALYZED,
                features,
                "",
                "",
                System.currentTimeMillis(),
            )

        @JvmStatic
        fun decodeFeatures(encoded: String?): DoubleArray {
            if (encoded.isNullOrBlank()) return DoubleArray(0)
            val parts = Pattern.compile(",").split(encoded, -1)
            return try {
                sanitize(DoubleArray(parts.size) { index -> parts[index].toDouble() })
            } catch (_: NumberFormatException) {
                DoubleArray(0)
            }
        }

        private fun sanitize(source: DoubleArray?): DoubleArray {
            val copy = source?.clone() ?: return DoubleArray(0)
            for (index in copy.indices) {
                if (!copy[index].isFinite()) copy[index] = 0.0
            }
            return copy
        }
    }
}
