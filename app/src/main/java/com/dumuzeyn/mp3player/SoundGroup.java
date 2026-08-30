package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;

final class SoundGroup {
    final String id;
    final String nameRussian;
    final String nameEnglish;
    final double[] centroid;
    final ArrayList<String> trackIds;

    SoundGroup(String id, String nameRussian, String nameEnglish, double[] centroid,
            List<String> trackIds) {
        this.id = id == null ? "" : id;
        this.nameRussian = nameRussian == null ? "" : nameRussian;
        this.nameEnglish = nameEnglish == null ? "" : nameEnglish;
        this.centroid = centroid == null ? new double[0] : centroid.clone();
        this.trackIds = new ArrayList<>(trackIds == null
                ? java.util.Collections.emptyList() : trackIds);
    }

    SoundGroup named(String russian, String english) {
        return new SoundGroup(id, russian, english, centroid, trackIds);
    }
}
