package com.bolohisab.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun `decrypt recovers the original bytes with the right passphrase`() {
        val plain = "সাড়ে তিনশো টাকা বাকি".toByteArray(Charsets.UTF_8)
        val blob = BackupCrypto.encrypt(plain, "correct horse")
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, "correct horse"))
    }

    @Test
    fun `decrypt rejects a wrong passphrase`() {
        val blob = BackupCrypto.encrypt("hello".toByteArray(), "right-pass")
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(blob, "wrong-pass")
        }
    }

    @Test
    fun `decrypt rejects a corrupted or non-backup file`() {
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decrypt("not a backup file at all".toByteArray(), "any-pass")
        }
    }

    @Test
    fun `two backups of the same data use different salts and ciphertext`() {
        val plain = "same content".toByteArray()
        val a = BackupCrypto.encrypt(plain, "pass")
        val b = BackupCrypto.encrypt(plain, "pass")
        org.junit.Assert.assertFalse(a.contentEquals(b))
    }
}
