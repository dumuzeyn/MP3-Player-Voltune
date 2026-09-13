package com.dumuzeyn.mp3player

fun interface ValueProvider<T> {
    fun get(): T
}

fun interface BooleanValueProvider {
    fun get(): Boolean
}

fun interface IntValueProvider {
    fun get(): Int
}

fun interface TrackFinder {
    fun find(value: String): Track?
}

fun interface TrackPredicate {
    fun test(track: Track): Boolean
}
