package com.bolohisab.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "customers", indices = [Index("name")])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * One ledger entry. Money columns are poisha (Long), never floating point.
 *
 * [balanceDelta] is how much the entry moves the customer's due, so a balance is a
 * plain SUM. Entries are never hard-deleted: [deletedAt] keeps an audit trail and
 * makes undo trivial.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("customer_id"), Index("created_at")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "customer_id") val customerId: Long?,
    val type: String,
    @ColumnInfo(name = "total_poisha") val totalPoisha: Long,
    @ColumnInfo(name = "paid_poisha") val paidPoisha: Long,
    @ColumnInfo(name = "balance_delta") val balanceDelta: Long,
    val note: String?,
    val transcript: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "entry_items",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entry_id"), Index("name")],
)
data class EntryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    @ColumnInfo(name = "price_poisha") val pricePoisha: Long?,
)

/** A snapshot of an entry's financial facts just before an edit overwrote them. */
@Entity(
    tableName = "entry_history",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entry_id")],
)
data class EntryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
    val type: String,
    @ColumnInfo(name = "total_poisha") val totalPoisha: Long,
    @ColumnInfo(name = "paid_poisha") val paidPoisha: Long,
    @ColumnInfo(name = "balance_delta") val balanceDelta: Long,
    val note: String?,
    val transcript: String,
)

/** A tracked product. [stockQty] and [lowStockThreshold] use the same unit, if any. */
@Entity(tableName = "products", indices = [Index("name")])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String?,
    @ColumnInfo(name = "stock_qty") val stockQty: Double,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class EntryWithDetails(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "customer_id", entityColumn = "id")
    val customer: CustomerEntity?,
    @Relation(parentColumn = "id", entityColumn = "entry_id")
    val items: List<EntryItemEntity>,
)

data class CustomerBalanceRow(
    @Embedded val customer: CustomerEntity,
    @ColumnInfo(name = "balance") val balance: Long,
    @ColumnInfo(name = "last_activity") val lastActivity: Long?,
)

data class TypeTotalRow(
    val type: String,
    @ColumnInfo(name = "total") val total: Long,
    @ColumnInfo(name = "paid") val paid: Long,
    @ColumnInfo(name = "count") val count: Int,
)

// ------------------------------------------------------------------ learning (typing help)

/** A Banglish spelling and the Bangla (or the Latin itself) the shopkeeper picked for it. */
@Entity(tableName = "typing_choices", primaryKeys = ["typed", "text"])
data class TypingChoiceEntity(
    val typed: String,
    val text: String,
    val count: Int,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)

/** How often a word appears in saved entries. */
@Entity(tableName = "word_usage")
data class WordUsageEntity(
    @PrimaryKey val word: String,
    val count: Int,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)

/** Which word followed which in saved entries; [prev] may be the "<number>" slot. */
@Entity(tableName = "word_pairs", primaryKeys = ["prev", "next"])
data class WordPairEntity(
    val prev: String,
    val next: String,
    val count: Int,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)

/** A word speech recognition got wrong and what the shopkeeper corrected it to. */
@Entity(tableName = "corrections")
data class CorrectionEntity(
    @PrimaryKey val heard: String,
    val fixed: String,
    val count: Int,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)
