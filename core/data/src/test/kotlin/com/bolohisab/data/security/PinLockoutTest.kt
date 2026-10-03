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
}
