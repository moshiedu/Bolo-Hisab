package com.bolohisab.data.backup

import com.bolohisab.data.db.CustomerEntity
import com.bolohisab.data.db.EntryEntity
import com.bolohisab.data.db.EntryItemEntity
import com.bolohisab.data.db.LedgerDao
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Turns the whole ledger into an encrypted, portable snapshot, and back. */
@Singleton
class BackupManager @Inject constructor(
    private val dao: LedgerDao,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(passphrase: String): ByteArray {
        val customers = dao.allCustomersOnce().map {
            BackupCustomer(it.id, it.name, it.phone, it.address, it.photoPath, it.createdAt)
        }
        val entries = dao.allEntriesWithItemsOnce().map { d ->
            BackupEntry(
                id = d.entry.id,
                customerId = d.entry.customerId,
                type = d.entry.type,
                totalPoisha = d.entry.totalPoisha,
                paidPoisha = d.entry.paidPoisha,
                balanceDelta = d.entry.balanceDelta,
                note = d.entry.note,
                transcript = d.entry.transcript,
                createdAt = d.entry.createdAt,
                deletedAt = d.entry.deletedAt,
                items = d.items.map { BackupItem(it.name, it.quantity, it.unit, it.pricePoisha) },
            )
        }
        val payload = BackupPayload(BACKUP_FORMAT_VERSION, clock.millis(), customers, entries)
        val plain = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return BackupCrypto.encrypt(plain, passphrase)
    }

    /** Wipes the ledger and reloads it from [blob]. Throws [BackupCrypto.WrongPassphraseException] on a bad passphrase. */
    suspend fun import(blob: ByteArray, passphrase: String) {
        val plain = BackupCrypto.decrypt(blob, passphrase)
        val payload = json.decodeFromString(BackupPayload.serializer(), plain.toString(Charsets.UTF_8))

        val customers = payload.customers.map {
            CustomerEntity(it.id, it.name, it.phone, it.address, it.photoPath, it.createdAt)
        }
        val entries = payload.entries.map {
            EntryEntity(
                id = it.id,
                customerId = it.customerId,
                type = it.type,
                totalPoisha = it.totalPoisha,
                paidPoisha = it.paidPoisha,
                balanceDelta = it.balanceDelta,
                note = it.note,
                transcript = it.transcript,
                createdAt = it.createdAt,
                deletedAt = it.deletedAt,
            )
        }
        val items = payload.entries.flatMap { e ->
            e.items.map { EntryItemEntity(entryId = e.id, name = it.name, quantity = it.quantity, unit = it.unit, pricePoisha = it.pricePoisha) }
        }
        dao.replaceAll(customers, entries, items)
    }
}
