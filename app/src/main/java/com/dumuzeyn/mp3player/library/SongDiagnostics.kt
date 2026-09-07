package com.dumuzeyn.mp3player.library

import android.content.Context
import com.dumuzeyn.mp3player.Track
import com.dumuzeyn.mp3player.TrackStore
import com.dumuzeyn.mp3player.VoltuneLog

object SongDiagnostics {
    @JvmStatic
    fun inspect(context: Context, tracks: List<Track>): Result {
        var available = 0
        var unavailable = 0
        var withDuration = 0
        var withoutDuration = 0
        val problemTitles = StringBuilder()

        tracks.forEach { track ->
            if (TrackStore.canOpenForRead(context, track.asUri())) {
                available++
            } else {
                unavailable++
                if (problemTitles.length < MAX_PROBLEM_TITLES_LENGTH) {
                    problemTitles.append("\n- ").append(track.title)
                }
            }

            if (track.durationMs > 0) withDuration++ else withoutDuration++
        }

        return Result(
            available,
            unavailable,
            withDuration,
            withoutDuration,
            problemTitles.toString(),
        ).also { VoltuneLog.info(it.toLogMessage()) }
    }

    class Result(
        @JvmField val available: Int,
        @JvmField val unavailable: Int,
        @JvmField val withDuration: Int,
        @JvmField val withoutDuration: Int,
        @JvmField val problemTitles: String,
    ) {
        internal fun toLogMessage(): String =
            "song_diagnostics available=$available unavailable=$unavailable " +
                "withDuration=$withDuration withoutDuration=$withoutDuration"
    }

    private const val MAX_PROBLEM_TITLES_LENGTH = 500
}
