package com.bolohisab.data.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted SHA-256 for the app-lock PIN. A verifier, not a secret store: the PIN itself is
 * never kept, so this never needs the Android Keystore the way [DatabaseKeyProvider] does.
 */
object PinHasher {

    data class Salted(val saltHex: String, val hashHex: String)

    fun hash(pin: String, saltHex: String = randomSaltHex()): Salted {
        val salt = saltHex.hexToBytes()
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(salt)
            update(pin.toByteArray(Charsets.UTF_8))
        }.digest()
        return Salted(saltHex, digest.toHex())
    }

    fun matches(pin: String, saltHex: String, expectedHashHex: String): Boolean =
        hash(pin, saltHex).hashHex == expectedHashHex

    private fun randomSaltHex(): String = ByteArray(16).also(SecureRandom()::nextBytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
