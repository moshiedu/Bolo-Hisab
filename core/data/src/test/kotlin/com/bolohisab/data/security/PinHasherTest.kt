package com.bolohisab.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

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

    @Test
    fun `new hashes are pbkdf2 and need no upgrade`() {
        val salted = PinHasher.hash("2468")
        assertTrue(salted.hashHex.startsWith("pbkdf2:"))
        assertFalse(PinHasher.needsUpgrade(salted.hashHex))
    }

    @Test
    fun `legacy sha256 hashes still verify and are flagged for upgrade`() {
        val salt = "00112233445566778899aabbccddeeff"
        val legacy = MessageDigest.getInstance("SHA-256").apply {
            update(ByteArray(salt.length / 2) { i -> salt.substring(i * 2, i * 2 + 2).toInt(16).toByte() })
            update("2468".toByteArray(Charsets.UTF_8))
        }.digest().joinToString("") { "%02x".format(it) }

        assertTrue(PinHasher.matches("2468", salt, legacy))
        assertFalse(PinHasher.matches("1357", salt, legacy))
        assertTrue(PinHasher.needsUpgrade(legacy))
    }
}
