package com.dumuzeyn.mp3player

import java.util.UUID

internal data class AudioEditClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val title: String,
    val sourceDurationMs: Long,
    val startMs: Long = 0,
    val endMs: Long = sourceDurationMs,
    val lane: Int = 0,
    val offsetMs: Long = 0,
    val gain: Float = 1f,
) {
    val durationMs: Long get() = endMs - startMs
    val finishMs: Long get() = offsetMs + durationMs

    init {
        require(uri.isNotBlank() && sourceDurationMs in 1..MAX_TIME_MS)
        require(startMs >= 0 && endMs > startMs && endMs <= sourceDurationMs)
        require(lane in 0 until MAX_LANES && offsetMs >= 0 && finishMs <= MAX_TIME_MS)
        require(gain.isFinite() && gain in 0f..1f)
    }

    companion object {
        const val MAX_LANES = 8
        const val MAX_TIME_MS = 86_400_000L
    }
}

internal data class AudioEditProject(val clips: List<AudioEditClip> = emptyList()) {
    val durationMs: Long get() = clips.maxOfOrNull(AudioEditClip::finishMs) ?: 0

    init {
        require(clips.size <= 200 && clips.map { it.id }.distinct().size == clips.size)
        clips.groupBy { it.lane }.values.forEach { lane ->
            lane.sortedBy { it.offsetMs }.zipWithNext().forEach { (left, right) ->
                require(left.finishMs <= right.offsetMs) { "Clips overlap on the same lane" }
            }
        }
    }

    fun replace(clip: AudioEditClip): AudioEditProject {
        require(clips.any { it.id == clip.id })
        return copy(clips = clips.map { if (it.id == clip.id) clip else it })
    }

    fun append(clip: AudioEditClip, lane: Int): AudioEditProject = copy(clips = clips + clip.copy(
        lane = lane, offsetMs = clips.filter { it.lane == lane }.maxOfOrNull { it.finishMs } ?: 0,
    ))

    fun remove(id: String): AudioEditProject = copy(clips = clips.filterNot { it.id == id })

    fun split(id: String, sourcePositionMs: Long): AudioEditProject {
        val clip = clips.first { it.id == id }
        require(sourcePositionMs > clip.startMs && sourcePositionMs < clip.endMs)
        val left = clip.copy(endMs = sourcePositionMs)
        val right = clip.copy(id = UUID.randomUUID().toString(), startMs = sourcePositionMs,
            offsetMs = clip.offsetMs + left.durationMs)
        return copy(clips = clips.flatMap { if (it.id == id) listOf(left, right) else listOf(it) })
    }

    fun removeRange(id: String, fromMs: Long, toMs: Long): AudioEditProject {
        val clip = clips.first { it.id == id }
        require(fromMs >= clip.startMs && toMs <= clip.endMs && fromMs < toMs)
        val parts = buildList {
            if (fromMs > clip.startMs) add(clip.copy(endMs = fromMs))
            if (toMs < clip.endMs) add(clip.copy(id = UUID.randomUUID().toString(), startMs = toMs,
                offsetMs = clip.offsetMs + fromMs - clip.startMs))
        }
        val removed = toMs - fromMs
        return copy(clips = clips.flatMap {
            when {
                it.id == id -> parts
                it.lane == clip.lane && it.offsetMs >= clip.finishMs ->
                    listOf(it.copy(offsetMs = it.offsetMs - removed))
                else -> listOf(it)
            }
        })
    }

    fun concatenate(): AudioEditProject {
        var cursor = 0L
        return copy(clips = clips.sortedWith(compareBy<AudioEditClip> { it.lane }.thenBy { it.offsetMs }).map {
            it.copy(lane = 0, offsetMs = cursor).also { result -> cursor = result.finishMs }
        })
    }
}
