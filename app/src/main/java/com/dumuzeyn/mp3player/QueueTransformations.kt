package com.dumuzeyn.mp3player

import java.util.Collections
import java.util.Random

object QueueTransformations {
    @JvmStatic
    fun <T> move(source: List<T>, from: Int, to: Int): ArrayList<T> =
        ArrayList(source).apply {
            if (from in indices && to in indices && from != to) add(to, removeAt(from))
        }

    @JvmStatic
    fun <T> remove(source: List<T>, index: Int): ArrayList<T> =
        ArrayList(source).apply {
            if (index in indices) removeAt(index)
        }

    @JvmStatic
    fun <T> playNext(source: List<T>, item: T, currentIndex: Int): ArrayList<T> =
        ArrayList(source).apply {
            remove(item)
            add((currentIndex + 1).coerceIn(0, size), item)
        }

    @JvmStatic
    @JvmOverloads
    fun <T> randomSubset(
        source: List<T>,
        requestedCount: Int,
        random: Random = Random(),
    ): ArrayList<T> {
        if (source.isEmpty()) return ArrayList()
        val shuffled = ArrayList(source)
        Collections.shuffle(shuffled, random)
        return ArrayList(shuffled.subList(0, requestedCount.coerceIn(1, shuffled.size)))
    }
}
