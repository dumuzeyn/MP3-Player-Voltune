package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.HashSet;

/** Mutable library data owned by the activity graph. */
public final class LibraryState {
    final ArrayList<Track> tracks = new ArrayList<>();
    final HashSet<String> favorites = new HashSet<>();
    final ArrayList<Playlist> playlists = new ArrayList<>();
    HomeContent homeContent = HomeContent.empty();
}
