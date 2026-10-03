package com.bolohisab.data.backup

import kotlinx.serialization.Serializable

/**
 * A full snapshot of the ledger, independent of the Room schema so it stays readable across
 * app versions. IDs are kept exactly as stored, so restoring re-links entries to customers
 * and items to entries without remapping.
 */
@Serializable
data class BackupPayload(
    val version: Int,
    val exportedAt: Long,
    val customers: List<BackupCustomer>,
    val entries: List<BackupEntry>,
    /** Absent in version 1 files, which predate stock tracking in backups. */
    val products: List<BackupProduct> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    /** What the typing help learned. Null in files before version 3: restoring keeps the phone's own. */
    val learning: BackupLearning? = null,
)

@Serializable
data class BackupCustomer(
    val id: Long,
    val name: String,
    val phone: String?,
    val address: String? = null,
    /** Where the photo lived on the old phone; informational only, never used as a path on restore. */
    val photoPath: String? = null,
    val createdAt: Long,
    /** The photo itself (Base64 JPEG, see [BackupPhotos]); absent before version 4. */
    val photo: String? = null,
)

@Serializable
data class BackupEntry(
    val id: Long,
    val customerId: Long?,
    val type: String,
    val totalPoisha: Long,
    val paidPoisha: Long,
    val balanceDelta: Long,
    val note: String?,
    val transcript: String,
    val createdAt: Long,
    val deletedAt: Long?,
    val items: List<BackupItem>,
)

@Serializable
data class BackupItem(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val pricePoisha: Long?,
)

@Serializable
data class BackupProduct(
    val id: Long,
    val name: String,
    val unit: String?,
    val stockQty: Double,
    val lowStockThreshold: Double?,
    val createdAt: Long,
)

@Serializable
data class BackupHistory(
    val entryId: Long,
    val changedAt: Long,
    val type: String,
    val totalPoisha: Long,
    val paidPoisha: Long,
    val balanceDelta: Long,
    val note: String?,
    val transcript: String,
)

@Serializable
data class BackupLearning(
    val choices: List<BackupChoice> = emptyList(),
    val words: List<BackupWordUsage> = emptyList(),
    val pairs: List<BackupWordPair> = emptyList(),
    val corrections: List<BackupCorrection> = emptyList(),
)

@Serializable
data class BackupChoice(val typed: String, val text: String, val count: Int, val lastUsed: Long)

@Serializable
data class BackupWordUsage(val word: String, val count: Int, val lastUsed: Long)

@Serializable
data class BackupWordPair(val prev: String, val next: String, val count: Int, val lastUsed: Long)

@Serializable
data class BackupCorrection(val heard: String, val fixed: String, val count: Int, val lastUsed: Long)

/** 2 adds products and entry edit history; 3 adds what the typing help learned; 4 adds customer photos. */
const val BACKUP_FORMAT_VERSION = 4
