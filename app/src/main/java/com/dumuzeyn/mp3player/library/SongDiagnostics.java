package com.dumuzeyn.mp3player.library;

import android.content.Context;
import com.dumuzeyn.mp3player.Track;
import com.dumuzeyn.mp3player.TrackStore;
import com.dumuzeyn.mp3player.VoltuneLog;
import java.util.List;

public final class SongDiagnostics {
    private static final int MAX_PROBLEM_TITLES_LENGTH = 500;

    private SongDiagnostics() {
    }

    public static Result inspect(Context context, List<Track> tracks) {
        int available = 0;
        int unavailable = 0;
        int withDuration = 0;
        int withoutDuration = 0;
        StringBuilder problemTitles = new StringBuilder();

        for (Track track : tracks) {
            if (TrackStore.canOpenForRead(context, track.asUri())) {
                available++;
            } else {
                unavailable++;
                if (problemTitles.length() < MAX_PROBLEM_TITLES_LENGTH) {
                    problemTitles.append("\n- ").append(track.title);
                }
            }

            if (track.durationMs > 0) {
                withDuration++;
            } else {
                withoutDuration++;
            }
        }

        Result result = new Result(
                available, unavailable, withDuration, withoutDuration, problemTitles.toString());
        VoltuneLog.info(result.toLogMessage());
        return result;
    }

    public static final class Result {
        public final int available;
        public final int unavailable;
        public final int withDuration;
        public final int withoutDuration;
        public final String problemTitles;

        Result(
                int available,
                int unavailable,
                int withDuration,
                int withoutDuration,
                String problemTitles) {
            this.available = available;
            this.unavailable = unavailable;
            this.withDuration = withDuration;
            this.withoutDuration = withoutDuration;
            this.problemTitles = problemTitles;
        }

        private String toLogMessage() {
            return "song_diagnostics available=" + available
                    + " unavailable=" + unavailable
                    + " withDuration=" + withDuration
                    + " withoutDuration=" + withoutDuration;
        }
    }
}
