package com.bolohisab.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // ---------------------------------------------------------------- customers

    @Insert
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Query("UPDATE customers SET phone = :phone WHERE id = :id")
    suspend fun updateCustomerPhone(id: Long, phone: String?)

    @Query("UPDATE customers SET name = :name, phone = :phone, address = :address, photo_path = :photoPath WHERE id = :id")
    suspend fun updateCustomer(id: Long, name: String, phone: String?, address: String?, photoPath: String?)

    @Query("SELECT * FROM customers ORDER BY name")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT c.*, COALESCE(SUM(e.balance_delta), 0) AS balance, MAX(e.created_at) AS last_activity
        FROM customers c
        LEFT JOIN entries e ON e.customer_id = c.id AND e.deleted_at IS NULL
        GROUP BY c.id
        ORDER BY balance DESC, c.name
        """,
    )
    fun observeCustomerBalances(): Flow<List<CustomerBalanceRow>>

    @Query(
        """
        SELECT c.*, COALESCE(SUM(e.balance_delta), 0) AS balance, MAX(e.created_at) AS last_activity
        FROM customers c
        LEFT JOIN entries e ON e.customer_id = c.id AND e.deleted_at IS NULL
        WHERE c.id = :customerId
        GROUP BY c.id
        """,
    )
    fun observeCustomerBalance(customerId: Long): Flow<CustomerBalanceRow?>

    @Query(
        """
        SELECT COALESCE(SUM(balance_delta), 0) FROM entries
        WHERE customer_id = :customerId AND deleted_at IS NULL
        """,
    )
    suspend fun balanceOf(customerId: Long): Long

    // ------------------------------------------------------------------ entries

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Insert
    suspend fun insertItems(items: List<EntryItemEntity>)

    @Transaction
    suspend fun insertEntryWithItems(entry: EntryEntity, items: List<EntryItemEntity>): Long {
        val id = insertEntry(entry)
        if (items.isNotEmpty()) insertItems(items.map { it.copy(entryId = id) })
        return id
    }

    @Query("UPDATE entries SET deleted_at = :at WHERE id = :entryId")
    suspend fun softDelete(entryId: Long, at: Long)

    @Query("UPDATE entries SET deleted_at = NULL WHERE id = :entryId")
    suspend fun restore(entryId: Long)

    @Transaction
    @Query("SELECT * FROM entries WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EntryWithDetails>>

    @Transaction
    @Query(
        """
        SELECT * FROM entries WHERE customer_id = :customerId AND deleted_at IS NULL
        ORDER BY created_at DESC
        """,
    )
    fun observeForCustomer(customerId: Long): Flow<List<EntryWithDetails>>

    @Query(
        """
        SELECT type, COALESCE(SUM(total_poisha), 0) AS total, COALESCE(SUM(paid_poisha), 0) AS paid,
               COUNT(*) AS count
        FROM entries
        WHERE deleted_at IS NULL AND created_at >= :from AND created_at < :to
        GROUP BY type
        """,
    )
    fun observeTotals(from: Long, to: Long): Flow<List<TypeTotalRow>>

    @Query(
        """
        SELECT type, COALESCE(SUM(total_poisha), 0) AS total, COALESCE(SUM(paid_poisha), 0) AS paid,
               COUNT(*) AS count
        FROM entries
        WHERE deleted_at IS NULL AND created_at >= :from AND created_at < :to
        GROUP BY type
        """,
    )
    suspend fun totals(from: Long, to: Long): List<TypeTotalRow>

    @Query("SELECT DISTINCT name FROM entry_items ORDER BY name")
    fun observeItemNames(): Flow<List<String>>

    // -------------------------------------------------------------- editing

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entryById(id: Long): EntryEntity?

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Insert
    suspend fun insertHistory(history: EntryHistoryEntity)

    @Query("DELETE FROM entry_items WHERE entry_id = :entryId")
    suspend fun clearItemsFor(entryId: Long)

    @Query("SELECT * FROM entry_history WHERE entry_id = :entryId ORDER BY changed_at DESC")
    suspend fun historyFor(entryId: Long): List<EntryHistoryEntity>

    @Query("SELECT * FROM entry_items WHERE entry_id = :entryId")
    suspend fun itemsForEntryOnce(entryId: Long): List<EntryItemEntity>

    /** Snapshots the entry's current values into history, then overwrites it with the edit. */
    @Transaction
    suspend fun editEntry(history: EntryHistoryEntity, updated: EntryEntity, items: List<EntryItemEntity>) {
        insertHistory(history)
        clearItemsFor(updated.id)
        updateEntry(updated)
        if (items.isNotEmpty()) insertItems(items.map { it.copy(entryId = updated.id) })
    }

    // ------------------------------------------------------------------- backup

    @Query("SELECT * FROM customers")
    suspend fun allCustomersOnce(): List<CustomerEntity>

    @Transaction
    @Query("SELECT * FROM entries")
    suspend fun allEntriesWithItemsOnce(): List<EntryWithDetails>

    @Insert
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Insert
    suspend fun insertEntries(entries: List<EntryEntity>)

    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()

    @Query("SELECT * FROM entry_history")
    suspend fun allHistoryOnce(): List<EntryHistoryEntity>

    @Insert
    suspend fun insertHistories(history: List<EntryHistoryEntity>)

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Insert
    suspend fun insertProducts(products: List<ProductEntity>)

    /**
     * Wipes the ledger and reloads it from a backup, preserving the original ids so cross-references
     * still line up. [products] null leaves the current stock list alone (a backup made before
     * products were exported).
     */
    @Transaction
    suspend fun replaceAll(
        customers: List<CustomerEntity>,
        entries: List<EntryEntity>,
        items: List<EntryItemEntity>,
        history: List<EntryHistoryEntity>,
        products: List<ProductEntity>?,
    ) {
        clearEntries() // cascades entry_items and entry_history
        clearCustomers()
        insertCustomers(customers)
        insertEntries(entries)
        if (items.isNotEmpty()) insertItems(items)
        if (history.isNotEmpty()) insertHistories(history)
        if (products != null) {
            clearProducts()
            if (products.isNotEmpty()) insertProducts(products)
        }
    }

    // -------------------------------------------------------------- products

    @Insert
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("SELECT * FROM products ORDER BY name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun allProductsOnce(): List<ProductEntity>

    @Query("UPDATE products SET stock_qty = stock_qty + :delta WHERE id = :id")
    suspend fun adjustStock(id: Long, delta: Double)
}
