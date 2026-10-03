package com.bolohisab.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
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

    enum class Health {
        /** The key opens (or there is nothing yet: a fresh install). */
        OK,

        /**
         * A ledger exists but its key cannot be recovered: the Keystore key was wiped (some phones
         * do this after a lock-screen reset or an OS update) or the wrapped passphrase is gone.
         * The database can never be opened on this phone; only a backup brings the data back.
         */
        UNREADABLE,
    }

    /** Checks the key without creating or changing anything, so it is safe before the database opens. */
    fun health(): Health {
        val stored = prefs().getString(KEY_WRAPPED, null)
        val dbExists = context.getDatabasePath(DB_NAME).exists()
        if (stored == null) return if (dbExists) Health.UNREADABLE else Health.OK
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(ALIAS)) return Health.UNREADABLE
            unwrap(Base64.decode(stored, Base64.NO_WRAP))
            Health.OK
        } catch (e: java.security.GeneralSecurityException) {
            Health.UNREADABLE
        } catch (e: IllegalArgumentException) {
            Health.UNREADABLE
        }
    }

    /**
     * Starts over with a new key after [Health.UNREADABLE]. The unreadable database is renamed,
     * never deleted, in case it can be recovered later; the app then opens an empty ledger and the
     * shopkeeper restores a backup. Returns the file the old ledger was moved to, if any.
     */
    fun startOver(): File? {
        val db = context.getDatabasePath(DB_NAME)
        var kept: File? = null
        if (db.exists()) {
            val stamp = System.currentTimeMillis()
            kept = File(db.parentFile, "ledger-unreadable-$stamp.db")
            db.renameTo(kept)
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                File(db.path + suffix).takeIf { it.exists() }?.renameTo(File(kept.path + suffix))
            }
        }
        prefs().edit().remove(KEY_WRAPPED).commit()
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
        return kept
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

        /** Same as [com.bolohisab.data.db.LedgerDatabase.NAME]; kept here so this class stays standalone. */
        const val DB_NAME = "ledger.db"
    }
}
