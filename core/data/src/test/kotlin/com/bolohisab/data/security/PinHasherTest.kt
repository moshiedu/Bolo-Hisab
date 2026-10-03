package com.bolohisab.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `matches accepts the correct pin`() {
        val salted = PinHasher.hash("2468")
        assertTrue(PinHasher.matches("2468", salted.saltHex, salted.hashHex))
    }

    @Test
    fun `matches rejects a wrong pin`() {
        val salted = PinHasher.hash("2468")
        assertFalse(PinHasher.matches("1357", salted.saltHex, salted.hashHex))
    }

    @Test
    fun `same pin hashes differently under different salts`() {
        val a = PinHasher.hash("2468")
        val b = PinHasher.hash("2468")
        assertNotEquals(a.saltHex, b.saltHex)
        assertNotEquals(a.hashHex, b.hashHex)
    }

    @Test
    fun `hash is deterministic for a given salt`() {
        val a = PinHasher.hash("2468", saltHex = "aabbccdd")
        val b = PinHasher.hash("2468", saltHex = "aabbccdd")
        assertEquals(a.hashHex, b.hashHex)
    }
}
