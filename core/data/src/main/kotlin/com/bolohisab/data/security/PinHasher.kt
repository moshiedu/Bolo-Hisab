package com.bolohisab.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 for the app-lock PIN. A verifier, not a secret store: the PIN itself is
 * never kept, so this never needs the Android Keystore the way [DatabaseKeyProvider] does.
 *
 * A 4-digit PIN has only 10,000 values, so the hash must be slow: plain SHA-256 would let
 * anyone holding the prefs file try them all in milliseconds. Hashes written by older
 * builds (bare SHA-256 hex, no prefix) still verify; [needsUpgrade] tells the caller to
 * re-hash them after the next correct PIN.
 */
object PinHasher {

    data class Salted(val saltHex: String, val hashHex: String)

    private const val PREFIX = "pbkdf2:"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun hash(pin: String, saltHex: String = randomSaltHex()): Salted {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.hexToBytes(), ITERATIONS, KEY_BITS)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Salted(saltHex, PREFIX + derived.toHex())
    }

    fun matches(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        val actual = if (expectedHashHex.startsWith(PREFIX)) hash(pin, saltHex).hashHex else legacyHash(pin, saltHex)
        return MessageDigest.isEqual(actual.toByteArray(), expectedHashHex.toByteArray())
    }

    /** True for a hash stored by a build before PBKDF2. */
    fun needsUpgrade(hashHex: String): Boolean = !hashHex.startsWith(PREFIX)

    private fun legacyHash(pin: String, saltHex: String): String =
        MessageDigest.getInstance("SHA-256").apply {
            update(saltHex.hexToBytes())
            update(pin.toByteArray(Charsets.UTF_8))
        }.digest().toHex()

    private fun randomSaltHex(): String = ByteArray(16).also(SecureRandom()::nextBytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
