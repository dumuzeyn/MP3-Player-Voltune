package com.dumuzeyn.mp3player

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Adaptive clustering restored from the last pre-KMeans implementation. */
internal class SoundClusterEngine {
    fun cluster(source: List<TrackAudioProfile?>?): ArrayList<SoundGroup> {
        val profiles = usableProfiles(source)
        if (profiles.size < MIN_LIBRARY_SIZE) return ArrayList()
        val normalized = SoundFeatureNormalizer.normalize(profiles)
        val threshold = adaptiveThreshold(normalized.vectors)
        val clusters = ArrayList<MutableCluster>()
        for (index in profiles.indices) {
            addToNearest(
                clusters,
                profiles[index].trackId,
                normalized.vectors[index],
                threshold,
            )
        }
        mergeSmallClusters(clusters, max(2, (sqrt(profiles.size.toDouble()) / 4.0).roundToInt()))
        val adaptiveMaximum = max(2, ceil(sqrt(profiles.size.toDouble())).toInt())
        while (clusters.size > adaptiveMaximum) mergeClosestPair(clusters)
        val groups = ArrayList<SoundGroup>()
        for (cluster in clusters) {
            cluster.trackIds.sort()
            groups.add(
                SoundGroup(
                    stableId(cluster.trackIds),
                    "",
                    "",
                    cluster.centroid,
                    cluster.trackIds,
                ),
            )
        }
        return SoundGroupNamer.name(groups)
    }

    fun nearestGroup(
        rawFeatures: DoubleArray?,
        library: List<TrackAudioProfile?>?,
        groups: List<SoundGroup>?,
    ): String {
        if (
            rawFeatures == null || rawFeatures.size != TrackAudioProfile.FEATURE_COUNT ||
            groups.isNullOrEmpty()
        ) {
            return ""
        }
        val usable = usableProfiles(library)
        if (usable.isEmpty()) return ""
        val normalization = SoundFeatureNormalizer.normalize(usable)
        val vector = normalization.vector(rawFeatures)
        var nearest: SoundGroup? = null
        var distance = Double.POSITIVE_INFINITY
        for (group in groups) {
            val current = SoundFeatureNormalizer.distance(vector, group.centroid)
            if (current < distance) {
                distance = current
                nearest = group
            }
        }
        return nearest?.id.orEmpty()
    }

    private class MutableCluster(trackId: String, vector: DoubleArray) {
        val trackIds = arrayListOf(trackId)
        var centroid = vector.clone()

        fun add(trackId: String, vector: DoubleArray) {
            val previous = trackIds.size
            trackIds.add(trackId)
            for (index in centroid.indices) {
                centroid[index] = (centroid[index] * previous + vector[index]) / (previous + 1)
            }
        }

        fun merge(other: MutableCluster) {
            val ownSize = trackIds.size
            val otherSize = other.trackIds.size
            val total = ownSize + otherSize
            for (index in centroid.indices) {
                centroid[index] =
                    (centroid[index] * ownSize + other.centroid[index] * otherSize) / total
            }
            trackIds.addAll(other.trackIds)
        }
    }

    companion object {
        const val CLUSTERING_VERSION = 3
        private const val MIN_LIBRARY_SIZE = 4
        private const val DISTANCE_SAMPLE_LIMIT = 256

        private fun usableProfiles(
            source: List<TrackAudioProfile?>?,
        ): ArrayList<TrackAudioProfile> {
            val result = ArrayList<TrackAudioProfile>()
            if (source != null) {
                for (profile in source) {
                    if (profile != null && profile.usable()) result.add(profile)
                }
            }
            result.sortBy(TrackAudioProfile::trackId)
            return result
        }

        private fun adaptiveThreshold(vectors: List<DoubleArray>): Double {
            val count = min(vectors.size, DISTANCE_SAMPLE_LIMIT)
            val nearest = ArrayList<Double>()
            for (left in 0 until count) {
                var best = Double.POSITIVE_INFINITY
                for (right in 0 until count) {
                    if (left != right) {
                        best = min(
                            best,
                            SoundFeatureNormalizer.distance(vectors[left], vectors[right]),
                        )
                    }
                }
                if (best.isFinite()) nearest.add(best)
            }
            nearest.sort()
            val median = if (nearest.isEmpty()) 0.75 else nearest[nearest.size / 2]
            return max(0.35, min(1.75, median * 1.35))
        }

        private fun addToNearest(
            clusters: ArrayList<MutableCluster>,
            trackId: String,
            vector: DoubleArray,
            threshold: Double,
        ) {
            var nearest: MutableCluster? = null
            var distance = Double.POSITIVE_INFINITY
            for (cluster in clusters) {
                val current = SoundFeatureNormalizer.distance(vector, cluster.centroid)
                if (current < distance) {
                    distance = current
                    nearest = cluster
                }
            }
            if (nearest == null || distance > threshold) {
                clusters.add(MutableCluster(trackId, vector))
            } else {
                nearest.add(trackId, vector)
            }
        }

        private fun mergeSmallClusters(clusters: ArrayList<MutableCluster>, minimum: Int) {
            var changed = true
            while (changed && clusters.size > 1) {
                changed = false
                for (index in clusters.indices) {
                    val small = clusters[index]
                    if (small.trackIds.size >= minimum) continue
                    val nearest = nearestClusterIndex(clusters, index)
                    if (nearest >= 0) {
                        clusters[nearest].merge(small)
                        clusters.removeAt(index)
                        changed = true
                        break
                    }
                }
            }
        }

        private fun mergeClosestPair(clusters: ArrayList<MutableCluster>) {
            var bestLeft = 0
            var bestRight = 1
            var best = Double.POSITIVE_INFINITY
            for (left in clusters.indices) {
                for (right in left + 1 until clusters.size) {
                    val distance = SoundFeatureNormalizer.distance(
                        clusters[left].centroid,
                        clusters[right].centroid,
                    )
                    if (distance < best) {
                        best = distance
                        bestLeft = left
                        bestRight = right
                    }
                }
            }
            clusters[bestLeft].merge(clusters.removeAt(bestRight))
        }

        private fun nearestClusterIndex(clusters: ArrayList<MutableCluster>, source: Int): Int {
            var nearest = -1
            var best = Double.POSITIVE_INFINITY
            for (index in clusters.indices) {
                if (index == source) continue
                val distance = SoundFeatureNormalizer.distance(
                    clusters[source].centroid,
                    clusters[index].centroid,
                )
                if (distance < best) {
                    best = distance
                    nearest = index
                }
            }
            return nearest
        }

        private fun stableId(trackIds: List<String>): String {
            val joined = trackIds.joinToString("\n")
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(joined.toByteArray(StandardCharsets.UTF_8))
                buildString("sound-".length + 12) {
                    append("sound-")
                    for (index in 0 until 6) {
                        append(String.format(Locale.ROOT, "%02x", digest[index]))
                    }
                }
            } catch (_: Exception) {
                "sound-${Integer.toHexString(joined.hashCode())}"
            }
        }
    }
}
