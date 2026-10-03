package com.bolohisab.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.bolohisab.data.db.CorrectionEntity
import com.bolohisab.data.db.CustomerEntity
import com.bolohisab.data.db.EntryEntity
import com.bolohisab.data.db.EntryHistoryEntity
import com.bolohisab.data.db.EntryItemEntity
import com.bolohisab.data.db.LearningDao
import com.bolohisab.data.db.LedgerDao
import com.bolohisab.data.db.LedgerDatabase
import com.bolohisab.data.db.ProductEntity
import com.bolohisab.data.db.TypingChoiceEntity
import com.bolohisab.data.db.WordPairEntity
import com.bolohisab.data.db.WordUsageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Turns the whole ledger into an encrypted, portable snapshot, and back. */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LedgerDatabase,
    private val dao: LedgerDao,
    private val learningDao: LearningDao,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(passphrase: String): ByteArray {
        val customers = dao.allCustomersOnce().map {
            val photo = withContext(Dispatchers.IO) {
                it.photoPath?.let { p -> runCatching { File(p).readBytes() }.getOrNull() }?.let(BackupPhotos::encode)
            }
            BackupCustomer(it.id, it.name, it.phone, it.address, it.photoPath, it.createdAt, photo)
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
        val products = dao.allProductsOnce().map {
            BackupProduct(it.id, it.name, it.unit, it.stockQty, it.lowStockThreshold, it.createdAt)
        }
        val history = dao.allHistoryOnce().map {
            BackupHistory(it.entryId, it.changedAt, it.type, it.totalPoisha, it.paidPoisha, it.balanceDelta, it.note, it.transcript)
        }
        val learning = BackupLearning(
            choices = learningDao.allChoicesOnce().map { BackupChoice(it.typed, it.text, it.count, it.lastUsed) },
            words = learningDao.allWordsOnce().map { BackupWordUsage(it.word, it.count, it.lastUsed) },
            pairs = learningDao.allPairsOnce().map { BackupWordPair(it.prev, it.next, it.count, it.lastUsed) },
            corrections = learningDao.allCorrectionsOnce().map { BackupCorrection(it.heard, it.fixed, it.count, it.lastUsed) },
        )
        val payload = BackupPayload(BACKUP_FORMAT_VERSION, clock.millis(), customers, entries, products, history, learning)
        val plain = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return BackupCrypto.encrypt(plain, passphrase)
    }

    /** Wipes the ledger and reloads it from [blob]. Throws [BackupCrypto.WrongPassphraseException] on a bad passphrase. */
    suspend fun import(blob: ByteArray, passphrase: String) {
        val plain = BackupCrypto.decrypt(blob, passphrase)
        val payload = json.decodeFromString(BackupPayload.serializer(), plain.toString(Charsets.UTF_8))

        val photoDir = File(context.filesDir, BackupPhotos.DIR)
        val written = mutableListOf<File>()
        val customers = withContext(Dispatchers.IO) {
            payload.customers.map {
                CustomerEntity(it.id, it.name, it.phone, it.address, restorePhoto(it, photoDir, written), it.createdAt)
            }
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
        val history = payload.history.map {
            EntryHistoryEntity(
                entryId = it.entryId,
                changedAt = it.changedAt,
                type = it.type,
                totalPoisha = it.totalPoisha,
                paidPoisha = it.paidPoisha,
                balanceDelta = it.balanceDelta,
                note = it.note,
                transcript = it.transcript,
            )
        }
        // Version 1 files carry no products; keep the phone's stock list rather than wiping it.
        val products = if (payload.version >= 2) {
            payload.products.map { ProductEntity(it.id, it.name, it.unit, it.stockQty, it.lowStockThreshold, it.createdAt) }
        } else {
            null
        }
        val learning = payload.learning
        try {
            db.withTransaction {
                dao.replaceAll(customers, entries, items, history, products)
                if (learning != null) {
                    learningDao.replaceAll(
                        choices = learning.choices.map { TypingChoiceEntity(it.typed, it.text, it.count, it.lastUsed) },
                        words = learning.words.map { WordUsageEntity(it.word, it.count, it.lastUsed) },
                        pairs = learning.pairs.map { WordPairEntity(it.prev, it.next, it.count, it.lastUsed) },
                        corrections = learning.corrections.map { CorrectionEntity(it.heard, it.fixed, it.count, it.lastUsed) },
                    )
                }
            }
        } catch (e: Throwable) {
            written.forEach { it.delete() }
            throw e
        }
        // Photos of customers the restore replaced are no longer referenced by anyone.
        val kept = customers.mapNotNull { it.photoPath }.toSet()
        withContext(Dispatchers.IO) {
            photoDir.listFiles()?.filter { it.absolutePath !in kept }?.forEach { it.delete() }
        }
    }

    /**
     * Where a restored customer's photo lives on this phone. A photo carried in the backup is
     * written under a fresh name (the file name in the backup is never used as a path). Older
     * backups only have the old phone's path, kept when it is a photo already in this app's
     * photo folder, i.e. a restore on the same phone.
     */
    private fun restorePhoto(customer: BackupCustomer, dir: File, written: MutableList<File>): String? {
        BackupPhotos.decode(customer.photo)?.let { bytes ->
            dir.mkdirs()
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.writeBytes(bytes)
            written += file
            return file.absolutePath
        }
        val old = customer.photoPath?.let(::File) ?: return null
        val inPhotoDir = old.exists() && old.canonicalFile.parentFile == dir.canonicalFile
        return if (inPhotoDir) old.absolutePath else null
    }
}
