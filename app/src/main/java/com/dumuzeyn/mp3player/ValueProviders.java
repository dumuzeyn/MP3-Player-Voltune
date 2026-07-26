package com.dumuzeyn.mp3player;

interface ValueProvider<T> {
    T get();
}

interface BooleanValueProvider {
    boolean get();
}

interface IntValueProvider {
    int get();
}

interface TrackFinder {
    Track find(String value);
}

interface TrackPredicate {
    boolean test(Track track);
}
