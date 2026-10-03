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
)

@Serializable
data class BackupCustomer(
    val id: Long,
    val name: String,
    val phone: String?,
    val address: String? = null,
    val photoPath: String? = null,
    val createdAt: Long,
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

const val BACKUP_FORMAT_VERSION = 1
