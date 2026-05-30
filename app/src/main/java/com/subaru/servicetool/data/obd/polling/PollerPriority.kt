package com.subaru.servicetool.data.obd.polling

/**
 * Priority level for a sensor within a poller's internal queue.
 *
 * Each poller maintains three queues. Sensors in the HIGH queue are polled on
 * every iteration to achieve multi-Hz update rates. MEDIUM and LOW queues fire
 * at reduced rates controlled by each poller's cycle counters.
 */
enum class PollerPriority {
    /** Polled every iteration — target: multiple times per second. */
    HIGH,
    /** Polled every N iterations. */
    MEDIUM,
    /** Polled every M iterations — slow-changing values. */
    LOW,
}
