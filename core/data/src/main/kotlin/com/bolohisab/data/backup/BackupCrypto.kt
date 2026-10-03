package com.bolohisab.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based encryption for a portable backup file. Unlike [com.bolohisab.data.security.DatabaseKeyProvider],
 * which ties the database key to this device's Android Keystore, a backup must open on a
 * different phone — so the key here is derived from a passphrase the shopkeeper remembers,
 * with a random salt kept in the file (salts are not secret) and PBKDF2 to slow guessing.
 */
object BackupCrypto {
    private const val MAGIC = "BHB1"
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)

    class WrongPassphraseException : Exception("Wrong passphrase, or the backup file is corrupted")

    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt)) }
        return magicBytes + salt + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, passphrase: String): ByteArray {
        val headerSize = magicBytes.size + SALT_BYTES + IV_BYTES
        if (blob.size < headerSize || !blob.copyOfRange(0, magicBytes.size).contentEquals(magicBytes)) {
            throw WrongPassphraseException()
        }
        var offset = magicBytes.size
        val salt = blob.copyOfRange(offset, offset + SALT_BYTES).also { offset += SALT_BYTES }
        val iv = blob.copyOfRange(offset, offset + IV_BYTES).also { offset += IV_BYTES }
        val ciphertext = blob.copyOfRange(offset, blob.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                .apply { init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv)) }
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw WrongPassphraseException()
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }
}
