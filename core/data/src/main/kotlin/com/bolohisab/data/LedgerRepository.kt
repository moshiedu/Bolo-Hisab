package com.bolohisab.data

import androidx.room.withTransaction
import com.bolohisab.data.db.CustomerBalanceRow
import com.bolohisab.data.db.CustomerEntity
import com.bolohisab.data.db.EntryEntity
import com.bolohisab.data.db.EntryHistoryEntity
import com.bolohisab.data.db.EntryItemEntity
import com.bolohisab.data.db.EntryWithDetails
import com.bolohisab.data.db.LedgerDao
import com.bolohisab.data.db.LedgerDatabase
import com.bolohisab.data.db.ProductEntity
import com.bolohisab.data.db.TypeTotalRow
import com.bolohisab.nlu.CustomerMatcher
import com.bolohisab.nlu.CustomerRef
import com.bolohisab.nlu.EntryDraft
import com.bolohisab.nlu.EntryType
import com.bolohisab.nlu.ItemLine
import com.bolohisab.nlu.ItemMatcher
import com.bolohisab.nlu.KnownCustomer
import com.bolohisab.nlu.KnownItem
import com.bolohisab.nlu.Period
import com.bolohisab.nlu.Poisha
import com.bolohisab.nlu.QuantityUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class Customer(val id: Long, val name: String, val phone: String?, val address: String? = null, val photoPath: String? = null)

data class CustomerBalance(val customer: Customer, val due: Poisha, val lastActivity: Instant?)

data class LedgerEntry(
    val id: Long,
    val type: EntryType,
    val customer: Customer?,
    val items: List<ItemLine>,
    val total: Poisha,
    val paid: Poisha,
    val balanceDelta: Poisha,
    val note: String?,
    val transcript: String,
    val createdAt: Instant,
)

/** Totals for a period. "Sales" counts goods sold on cash and on credit. */
data class Summary(
    val sales: Poisha,
    val creditGiven: Poisha,
    val collected: Poisha,
    val expenses: Poisha,
    val entries: Int,
) {
    companion object {
        val EMPTY = Summary(Poisha.ZERO, Poisha.ZERO, Poisha.ZERO, Poisha.ZERO, 0)
    }
}

data class SavedEntry(val entryId: Long, val customerId: Long?)

data class Product(
    val id: Long,
    val name: String,
    val unit: QuantityUnit?,
    val stockQty: Double,
    val lowStockThreshold: Double?,
    val createdAt: Instant,
) {
    val isLow: Boolean get() = lowStockThreshold != null && stockQty <= lowStockThreshold
}

/** What an entry looked like just before an edit overwrote it. */
data class EntryHistorySnapshot(
    val changedAt: Instant,
    val type: EntryType,
    val total: Poisha,
    val paid: Poisha,
    val note: String?,
)

@Singleton
class LedgerRepository @Inject constructor(
    private val db: LedgerDatabase,
    private val dao: LedgerDao,
    private val clock: Clock,
) {
    val customers: Flow<List<KnownCustomer>> =
        dao.observeCustomers().map { list -> list.map { KnownCustomer(it.id, it.name) } }

    val itemNames: Flow<List<String>> = dao.observeItemNames()

    fun customerBalances(): Flow<List<CustomerBalance>> = dao.observeCustomerBalances().map { it.map(::toBalance) }

    fun customerBalance(customerId: Long): Flow<CustomerBalance?> =
        dao.observeCustomerBalance(customerId).map { it?.let(::toBalance) }

    fun recentEntries(limit: Int = 50): Flow<List<LedgerEntry>> = dao.observeRecent(limit).map { it.map(::toEntry) }

    fun entriesFor(customerId: Long): Flow<List<LedgerEntry>> =
        dao.observeForCustomer(customerId).map { it.map(::toEntry) }

    fun summary(period: Period): Flow<Summary> {
        val (from, to) = range(period)
        return dao.observeTotals(from, to).map(::toSummary)
    }

    suspend fun summaryNow(period: Period): Summary {
        val (from, to) = range(period)
        return toSummary(dao.totals(from, to))
    }

    suspend fun dueOf(customerId: Long): Poisha = Poisha(dao.balanceOf(customerId))

    suspend fun topDebtors(limit: Int = 3): List<CustomerBalance> =
        customerBalances().first().filter { it.due.value > 0 }.take(limit)

    /** Creates a customer directly, independent of recording an entry (e.g. from the Customers tab). */
    suspend fun addCustomer(name: String, phone: String?, address: String? = null, photoPath: String? = null): Long =
        dao.insertCustomer(
            CustomerEntity(name = name.trim(), phone = phone, address = address, photoPath = photoPath, createdAt = clock.millis()),
        )

    /** Adds or replaces a customer's phone number, e.g. after picking them from contacts. */
    suspend fun setCustomerPhone(customerId: Long, phone: String) = dao.updateCustomerPhone(customerId, phone)

    /** Updates a customer's full profile (name/phone/address/photo) from the edit dialog. */
    suspend fun updateCustomer(id: Long, name: String, phone: String?, address: String?, photoPath: String?) =
        dao.updateCustomer(id, name.trim(), phone, address, photoPath)

    // ----------------------------------------------------------------- stock

    val products: Flow<List<Product>> = dao.observeProducts().map { list -> list.map(::toProduct) }

    suspend fun addProduct(name: String, unit: QuantityUnit?, openingStock: Double, lowStockThreshold: Double?): Long =
        dao.insertProduct(
            ProductEntity(
                name = name.trim(),
                unit = unit?.name,
                stockQty = openingStock,
                lowStockThreshold = lowStockThreshold,
                createdAt = clock.millis(),
            ),
        )

    suspend fun updateProduct(id: Long, name: String, unit: QuantityUnit?, lowStockThreshold: Double?) {
        val current = dao.allProductsOnce().firstOrNull { it.id == id } ?: return
        dao.updateProduct(current.copy(name = name.trim(), unit = unit?.name, lowStockThreshold = lowStockThreshold))
    }

    suspend fun restock(id: Long, qty: Double) = dao.adjustStock(id, qty)

    /**
     * Adjusts tracked products' stock for a sale's item lines. Only touches a product when the
     * item name confidently matches one ([ItemMatcher]) and units agree — an uncertain match
     * would silently corrupt the count, which is worse than not tracking it at all.
     */
    private suspend fun applyStockDelta(items: List<ItemLine>, sign: Int) {
        if (items.isEmpty()) return
        val products = dao.allProductsOnce()
        if (products.isEmpty()) return
        val matcher = ItemMatcher(products.map { KnownItem(it.id, it.name) })
        for (item in items) {
            val qty = item.quantity ?: continue
            val product = matcher.match(item.name)?.let { m -> products.firstOrNull { p -> p.id == m.item.id } } ?: continue
            val itemUnit = item.unit
            if (itemUnit != null && product.unit != null && itemUnit.name != product.unit) continue
            dao.adjustStock(product.id, sign * qty)
        }
    }

    /**
     * Id for the draft's customer. A "new" customer whose name is identical (after normalising
     * spacing, case and Unicode form) to an existing one reuses it rather than creating a twin.
     */
    private suspend fun resolveCustomerId(customer: CustomerRef?, now: Long): Long? = when (customer) {
        is CustomerRef.Existing -> customer.id
        is CustomerRef.New -> {
            val key = CustomerMatcher.nameKey(customer.name)
            dao.allCustomersOnce().firstOrNull { CustomerMatcher.nameKey(it.name) == key }?.id
                ?: dao.insertCustomer(CustomerEntity(name = customer.name.trim(), createdAt = now))
        }
        null -> null
    }

    /**
     * Saves a confirmed draft, creating the customer first when it is new. One transaction:
     * the new customer, the entry, its items and the stock change land together or not at all.
     */
    suspend fun save(draft: EntryDraft): SavedEntry = db.withTransaction {
        val now = clock.millis()
        val customerId = resolveCustomerId(draft.customer, now)
        val entry = EntryEntity(
            customerId = customerId,
            type = draft.type.name,
            totalPoisha = draft.total.value,
            paidPoisha = draft.paid.value,
            balanceDelta = draft.balanceDelta.value,
            note = draft.note,
            transcript = draft.transcript,
            createdAt = now,
        )
        val items = draft.items.map {
            EntryItemEntity(
                entryId = 0,
                name = it.name,
                quantity = it.quantity,
                unit = it.unit?.name,
                pricePoisha = it.price?.value,
            )
        }
        val saved = SavedEntry(dao.insertEntryWithItems(entry, items), customerId)
        applyStockDelta(draft.items, sign = -1)
        saved
    }

    /**
     * Soft-deletes an entry, giving back any stock it took. A no-op when it is already
     * deleted, so a double tap or a repeated undo cannot return the same stock twice.
     */
    suspend fun undo(entryId: Long): Unit = db.withTransaction {
        val current = dao.entryById(entryId) ?: return@withTransaction
        if (current.deletedAt != null) return@withTransaction
        applyStockDelta(dao.itemsForEntryOnce(entryId).map(::toItemLine), sign = 1)
        dao.softDelete(entryId, clock.millis())
    }

    /** Un-deletes an entry, taking its stock back out again. A no-op when it is not deleted. */
    suspend fun restore(entryId: Long): Unit = db.withTransaction {
        val current = dao.entryById(entryId) ?: return@withTransaction
        if (current.deletedAt == null) return@withTransaction
        applyStockDelta(dao.itemsForEntryOnce(entryId).map(::toItemLine), sign = -1)
        dao.restore(entryId)
    }

    /** Snapshots the current values into history, then applies [draft] to an existing entry. Returns the resolved customer id, if any. */
    suspend fun update(entryId: Long, draft: EntryDraft): Long? = db.withTransaction {
        val current = dao.entryById(entryId) ?: return@withTransaction null
        val oldItems = dao.itemsForEntryOnce(entryId).map(::toItemLine)
        val now = clock.millis()
        val customerId = resolveCustomerId(draft.customer, now)
        val history = EntryHistoryEntity(
            entryId = entryId,
            changedAt = now,
            type = current.type,
            totalPoisha = current.totalPoisha,
            paidPoisha = current.paidPoisha,
            balanceDelta = current.balanceDelta,
            note = current.note,
            transcript = current.transcript,
        )
        val updated = current.copy(
            customerId = customerId,
            type = draft.type.name,
            totalPoisha = draft.total.value,
            paidPoisha = draft.paid.value,
            balanceDelta = draft.balanceDelta.value,
            note = draft.note,
            transcript = draft.transcript,
        )
        val items = draft.items.map {
            EntryItemEntity(
                entryId = entryId,
                name = it.name,
                quantity = it.quantity,
                unit = it.unit?.name,
                pricePoisha = it.price?.value,
            )
        }
        dao.editEntry(history, updated, items)
        // A deleted entry already gave its stock back; only a live one moves stock on edit.
        if (current.deletedAt == null) {
            applyStockDelta(oldItems, sign = 1)
            applyStockDelta(draft.items, sign = -1)
        }
        customerId
    }

    suspend fun historyOf(entryId: Long): List<EntryHistorySnapshot> = dao.historyFor(entryId).map {
        EntryHistorySnapshot(
            changedAt = Instant.ofEpochMilli(it.changedAt),
            type = EntryType.valueOf(it.type),
            total = Poisha(it.totalPoisha),
            paid = Poisha(it.paidPoisha),
            note = it.note,
        )
    }

    // ---------------------------------------------------------------- mapping

    private fun range(period: Period): Pair<Long, Long> {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = when (period) {
            Period.TODAY -> today
            Period.YESTERDAY -> today.minusDays(1)
            Period.THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            Period.THIS_MONTH -> today.withDayOfMonth(1)
        }
        val end = if (period == Period.YESTERDAY) today else today.plusDays(1)
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to end.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun toSummary(rows: List<TypeTotalRow>): Summary {
        fun row(t: EntryType) = rows.firstOrNull { it.type == t.name }
        val cash = row(EntryType.CASH_SALE)
        val credit = row(EntryType.CREDIT_SALE)
        return Summary(
            sales = Poisha((cash?.total ?: 0) + (credit?.total ?: 0)),
            creditGiven = Poisha((credit?.total ?: 0) - (credit?.paid ?: 0)),
            collected = Poisha(row(EntryType.PAYMENT_RECEIVED)?.total ?: 0),
            expenses = Poisha(row(EntryType.EXPENSE)?.total ?: 0),
            entries = rows.sumOf { it.count },
        )
    }

    private fun toCustomer(e: CustomerEntity) = Customer(e.id, e.name, e.phone, e.address, e.photoPath)

    private fun toBalance(r: CustomerBalanceRow) =
        CustomerBalance(toCustomer(r.customer), Poisha(r.balance), r.lastActivity?.let(Instant::ofEpochMilli))

    private fun toEntry(d: EntryWithDetails) = LedgerEntry(
        id = d.entry.id,
        type = EntryType.valueOf(d.entry.type),
        customer = d.customer?.let(::toCustomer),
        items = d.items.sortedBy { it.id }.map(::toItemLine),
        total = Poisha(d.entry.totalPoisha),
        paid = Poisha(d.entry.paidPoisha),
        balanceDelta = Poisha(d.entry.balanceDelta),
        note = d.entry.note,
        transcript = d.entry.transcript,
        createdAt = Instant.ofEpochMilli(d.entry.createdAt),
    )

    private fun toItemLine(e: EntryItemEntity) = ItemLine(
        name = e.name,
        quantity = e.quantity,
        unit = e.unit?.let { u -> runCatching { QuantityUnit.valueOf(u) }.getOrNull() },
        price = e.pricePoisha?.let(::Poisha),
    )

    private fun toProduct(e: ProductEntity) = Product(
        id = e.id,
        name = e.name,
        unit = e.unit?.let { u -> runCatching { QuantityUnit.valueOf(u) }.getOrNull() },
        stockQty = e.stockQty,
        lowStockThreshold = e.lowStockThreshold,
        createdAt = Instant.ofEpochMilli(e.createdAt),
    )
}
