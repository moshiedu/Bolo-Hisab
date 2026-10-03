package com.bolohisab.data.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinLockoutTest {

    @Test
    fun `first mistakes are free`() {
        for (failures in 0 until PinLockout.FREE_ATTEMPTS) assertEquals(0L, PinLockout.lockoutMillis(failures))
    }

    @Test
    fun `wait doubles from thirty seconds`() {
        assertEquals(30_000L, PinLockout.lockoutMillis(PinLockout.FREE_ATTEMPTS))
        assertEquals(60_000L, PinLockout.lockoutMillis(PinLockout.FREE_ATTEMPTS + 1))
        assertEquals(120_000L, PinLockout.lockoutMillis(PinLockout.FREE_ATTEMPTS + 2))
    }

    @Test
    fun `wait is capped at fifteen minutes`() {
        assertEquals(PinLockout.MAX_MILLIS, PinLockout.lockoutMillis(PinLockout.FREE_ATTEMPTS + 5))
        assertEquals(PinLockout.MAX_MILLIS, PinLockout.lockoutMillis(1_000))
    }

    @Test
    fun `a lockout end pushed far ahead by a clock change is pulled in`() {
        val now = 1_000_000L
        assertEquals(now + PinLockout.MAX_MILLIS, PinLockout.effectiveEnd(now + 24 * 3_600_000L, now))
        assertEquals(now + 60_000L, PinLockout.effectiveEnd(now + 60_000L, now))
        assertEquals(null, PinLockout.effectiveEnd(now - 1, now))
        assertEquals(null, PinLockout.effectiveEnd(null, now))
    }
}
