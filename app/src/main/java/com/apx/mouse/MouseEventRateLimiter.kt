package com.apx.mouse

/**
 * Software-side event rate limiter for APX's input worker.
 *
 * This does not change the USB/Bluetooth HID device's physical polling rate.
 * The future input pipeline must call shouldProcess() for this setting to apply.
 */
class MouseEventRateLimiter(rateHz: Int) {
    private val intervalNanos = if (rateHz <= 0) 0L else 1_000_000_000L / rateHz
    private var lastAcceptedNanos = 0L

    @Synchronized
    fun shouldProcess(nowNanos: Long = System.nanoTime()): Boolean {
        if (intervalNanos == 0L) {
            lastAcceptedNanos = nowNanos
            return true
        }
        if (lastAcceptedNanos == 0L || nowNanos - lastAcceptedNanos >= intervalNanos) {
            lastAcceptedNanos = nowNanos
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        lastAcceptedNanos = 0L
    }
}
