package com.dumuzeyn.mp3player

/** Deterministic current-item selection after removing library tracks from a queue. */
data class QueueRemovalPlan(
    @JvmField val remaining: ArrayList<String>,
    @JvmField val currentIndex: Int,
    @JvmField val currentRemoved: Boolean,
) {
    companion object {
        @JvmStatic
        fun create(queue: List<String>, currentIndex: Int, removedIds: Set<String>): QueueRemovalPlan {
            val remaining = ArrayList<String>(queue.size)
            var retainedBeforeCurrent = 0
            val current = queue.getOrNull(currentIndex).orEmpty()
            queue.forEachIndexed { index, id ->
                if (id !in removedIds) {
                    remaining += id
                    if (index < currentIndex) retainedBeforeCurrent++
                }
            }
            val nextIndex = if (remaining.isEmpty()) 0 else retainedBeforeCurrent.coerceAtMost(remaining.lastIndex)
            return QueueRemovalPlan(remaining, nextIndex, current in removedIds)
        }
    }
}
