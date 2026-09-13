package com.dumuzeyn.mp3player

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
}
