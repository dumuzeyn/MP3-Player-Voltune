package com.dumuzeyn.mp3player

import kotlin.math.min
import kotlin.math.sqrt

/** Pre-KMeans standard scaling with tempo dimensions explicitly excluded. */
internal object SoundFeatureNormalizer {
    @JvmStatic
    fun normalize(profiles: List<TrackAudioProfile>?): Result {
        if (profiles.isNullOrEmpty()) {
            return Result(DoubleArray(0), DoubleArray(0), ArrayList())
        }

        val dimensions = TrackAudioProfile.FEATURE_COUNT
        val means = DoubleArray(dimensions)
        profiles.forEach { profile ->
            for (feature in 0 until dimensions) {
                if (included(feature)) means[feature] += profile.features[feature]
            }
        }
        for (feature in 0 until dimensions) {
            if (included(feature)) means[feature] /= profiles.size
        }

        val deviations = DoubleArray(dimensions)
        profiles.forEach { profile ->
            for (feature in 0 until dimensions) {
                if (!included(feature)) continue
                val delta = profile.features[feature] - means[feature]
                deviations[feature] += delta * delta
            }
        }
        for (feature in 0 until dimensions) {
            if (!included(feature)) {
                deviations[feature] = 1.0
                continue
            }
            deviations[feature] = sqrt(deviations[feature] / profiles.size)
            if (deviations[feature] < EPSILON) deviations[feature] = 1.0
        }

        val vectors = ArrayList<DoubleArray>(profiles.size)
        profiles.forEach { profile -> vectors.add(vector(profile.features, means, deviations)) }
        return Result(means, deviations, vectors)
    }

    @JvmStatic
    fun distance(left: DoubleArray, right: DoubleArray): Double {
        val count = min(left.size, right.size)
        var sum = 0.0
        var includedCount = 0
        for (index in 0 until count) {
            if (!included(index)) continue
            val delta = left[index] - right[index]
            sum += delta * delta
            includedCount++
        }
        return if (includedCount == 0) Double.POSITIVE_INFINITY else sqrt(sum / includedCount)
    }

    private fun vector(raw: DoubleArray, means: DoubleArray, deviations: DoubleArray): DoubleArray {
        val result = DoubleArray(TrackAudioProfile.FEATURE_COUNT)
        for (feature in result.indices) {
            if (!included(feature)) continue
            val value = if (feature < raw.size) raw[feature] else 0.0
            result[feature] = (value - means[feature]) / deviations[feature]
        }
        return result
    }

    private fun included(feature: Int): Boolean =
        feature != TrackAudioProfile.BPM && feature != TrackAudioProfile.TEMPO_CONFIDENCE

    class Result(
        @JvmField val means: DoubleArray,
        @JvmField val deviations: DoubleArray,
        @JvmField val vectors: ArrayList<DoubleArray>,
    ) {
        fun vector(raw: DoubleArray): DoubleArray =
            SoundFeatureNormalizer.vector(raw, means, deviations)
    }

    private const val EPSILON = 1.0e-9
}
