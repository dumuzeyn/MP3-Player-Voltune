package com.dumuzeyn.mp3player

/** Closes every registered activity-scoped resource even if one close operation fails. */
class CloseableRegistry {
    private val closeables = ArrayList<AutoCloseable>()
    private var closed = false

    fun add(closeable: AutoCloseable?) {
        if (closeable == null) return
        if (closed) {
            closeQuietly(closeable)
        } else {
            closeables += closeable
        }
    }

    fun closeAll() {
        if (closed) return
        closed = true
        closeables.asReversed().forEach(::closeQuietly)
        closeables.clear()
    }

    private fun closeQuietly(closeable: AutoCloseable) {
        try {
            closeable.close()
        } catch (_: Exception) {
            // One faulty resource must not prevent the remaining resources from closing.
        }
    }
}
