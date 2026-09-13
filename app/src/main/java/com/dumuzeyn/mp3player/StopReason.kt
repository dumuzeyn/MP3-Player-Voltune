package com.dumuzeyn.mp3player

enum class StopReason {
    NONE,
    USER,
    QUEUE_ENDED,
    SLEEP_TIMER,
    ALL_ITEMS_UNAVAILABLE,
    FATAL_ERROR,
    SERVICE_DESTROYED,
}
