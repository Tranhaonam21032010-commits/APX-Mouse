package com.apx.mouse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseEventRateLimiterTest {
    @Test
    fun unlimitedModeAcceptsEveryEvent() {
        val limiter = MouseEventRateLimiter(0)
        assertTrue(limiter.shouldProcess(1L))
        assertTrue(limiter.shouldProcess(2L))
        assertTrue(limiter.shouldProcess(3L))
    }

    @Test
    fun limitedModeRejectsEventsInsideInterval() {
        val limiter = MouseEventRateLimiter(125)
        assertTrue(limiter.shouldProcess(1_000_000_000L))
        assertFalse(limiter.shouldProcess(1_007_999_999L))
        assertTrue(limiter.shouldProcess(1_008_000_000L))
    }

    @Test
    fun resetAllowsNextEventImmediately() {
        val limiter = MouseEventRateLimiter(500)
        assertTrue(limiter.shouldProcess(1_000_000_000L))
        assertFalse(limiter.shouldProcess(1_001_000_000L))
        limiter.reset()
        assertTrue(limiter.shouldProcess(1_001_000_001L))
    }
}
