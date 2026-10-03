package com.bolohisab.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gives the SQLCipher passphrase for the ledger database.
 *
 * A random 32-byte passphrase is generated once, encrypted with an AES-GCM key that
 * never leaves the Android Keystore, and stored in no-backup preferences. Copying the
 * database file off the phone is therefore useless without this device's keystore.
 */
class DatabaseKeyProvider(private val context: Context) {

    fun passphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_WRAPPED, null)
        if (stored != null) return unwrap(Base64.decode(stored, Base64.NO_WRAP))

        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_WRAPPED, Base64.encodeToString(wrap(secret), Base64.NO_WRAP)).apply()
        return secret
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Output layout: 12-byte IV followed by ciphertext + GCM tag. */
    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "bolohisab_db_key"
        const val PREFS = "bolohisab_secure"
        const val KEY_WRAPPED = "db_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
