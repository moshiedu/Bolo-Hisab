package com.bolohisab.ui.record

import com.bolohisab.data.EntryHistorySnapshot
import com.bolohisab.data.LedgerEntry
import com.bolohisab.nlu.CustomerMatcher
import com.bolohisab.nlu.CustomerRef
import com.bolohisab.nlu.EntryDraft
import com.bolohisab.nlu.EntryType
import com.bolohisab.nlu.Field
import com.bolohisab.nlu.ItemLine
import com.bolohisab.nlu.KnownCustomer
import com.bolohisab.nlu.Poisha
import com.bolohisab.nlu.QuantityUnit
import com.bolohisab.ui.format.Bn

/** One editable item row on the confirm card. Text fields keep exactly what the user typed. */
data class ItemEdit(
    val key: Long,
    val name: String,
    val quantity: String,
    val unit: QuantityUnit?,
    val price: String,
)

enum class ReviewProblem { NEED_CUSTOMER, NEED_AMOUNT, PAID_TOO_MUCH }

/**
 * The confirm card's editable state. Built from the parser's draft, edited freely,
 * then turned back into a validated [EntryDraft] on save.
 */
data class ReviewState(
    val type: EntryType,
    val customerName: String,
    val matchedCustomerId: Long?,
    val items: List<ItemEdit>,
    /** Amount for entries without items (payments, expenses, bare credit). */
    val amount: String,
    val paid: String,
    val note: String?,
    val transcript: String,
    val uncertain: Set<Field>,
    /** Non-null when this card is editing an existing entry rather than recording a new one. */
    val editingEntryId: Long? = null,
    val history: List<EntryHistorySnapshot> = emptyList(),
    /** Set only when [customerName] came from the contact picker, so save() can attach it. */
    val pickedPhone: String? = null,
    /**
     * A total that differs from the item prices, e.g. "মোট ৩০০" after a discount on items
     * worth ৳320. While set it wins over the item sum; null means the total follows the items.
     */
    val totalOverride: String? = null,
    /** What the parser made of the transcript, kept so a save can learn from the shopkeeper's fixes. */
    val parsed: EntryDraft? = null,
) {
    val showsItems: Boolean get() = type == EntryType.CASH_SALE || type == EntryType.CREDIT_SALE
    val needsCustomer: Boolean get() = type == EntryType.CREDIT_SALE || type == EntryType.PAYMENT_RECEIVED
    val isNewCustomer: Boolean get() = matchedCustomerId == null && customerName.isNotBlank()

    val total: Poisha
        get() {
            val sum = itemSum
            if (showsItems && sum.value > 0) {
                return totalOverride?.let(Bn::parseAmount)?.let(Poisha::ofTaka) ?: sum
            }
            return Bn.parseAmount(amount)?.let(Poisha::ofTaka) ?: Poisha.ZERO
        }

    /** What the priced item rows add up to. */
    val itemSum: Poisha
        get() = Poisha(items.sumOf { Bn.parseAmount(it.price)?.let(Poisha::ofTaka)?.value ?: 0L })

    val paidAmount: Poisha
        get() = when (type) {
            EntryType.CREDIT_SALE -> Bn.parseAmount(paid)?.let(Poisha::ofTaka) ?: Poisha.ZERO
            else -> total
        }

    val due: Poisha get() = if (type == EntryType.CREDIT_SALE) total - paidAmount else Poisha.ZERO

    val problems: Set<ReviewProblem>
        get() = buildSet {
            if (needsCustomer && customerName.isBlank()) add(ReviewProblem.NEED_CUSTOMER)
            if (total.value <= 0) add(ReviewProblem.NEED_AMOUNT)
            if (type == EntryType.CREDIT_SALE && paidAmount > total) add(ReviewProblem.PAID_TOO_MUCH)
        }

    fun isFlagged(field: Field) = field in uncertain

    /** Keeps the amount typed so far when the user switches between entry types. */
    fun withType(newType: EntryType): ReviewState {
        val carried = if (showsItems && items.isNotEmpty() && total.value > 0) Bn.editable(total) else amount
        return copy(type = newType, amount = carried, uncertain = uncertain - Field.TYPE)
    }

    fun withCustomer(name: String, known: List<KnownCustomer>): ReviewState =
        copy(customerName = name, matchedCustomerId = exactMatch(name, known)?.id, uncertain = uncertain - Field.CUSTOMER, pickedPhone = null)

    /** Sets the customer name and phone together, from the system contact picker. */
    fun withContactPicked(name: String, phone: String, known: List<KnownCustomer>): ReviewState =
        copy(customerName = name, matchedCustomerId = exactMatch(name, known)?.id, uncertain = uncertain - Field.CUSTOMER, pickedPhone = phone)

    /**
     * An existing customer whose name is close to, but not exactly, what was typed ("রহীম" for
     * "রহিম"), so the card can ask "did you mean" before a near-duplicate customer is created.
     */
    fun nearMatch(known: List<KnownCustomer>): KnownCustomer? {
        if (!isNewCustomer) return null
        return CustomerMatcher(known).match(customerName.trim(), threshold = 0.75)?.customer
    }

    private fun exactMatch(name: String, known: List<KnownCustomer>): KnownCustomer? {
        val key = CustomerMatcher.nameKey(name)
        return if (key.isEmpty()) null else known.firstOrNull { CustomerMatcher.nameKey(it.name) == key }
    }

    fun toDraft(): EntryDraft {
        val customer: CustomerRef? = when {
            customerName.isBlank() -> null
            matchedCustomerId != null -> CustomerRef.Existing(matchedCustomerId, customerName.trim(), 1.0)
            else -> CustomerRef.New(customerName.trim())
        }
        val lines = if (showsItems) {
            items.filter { it.name.isNotBlank() }.map {
                ItemLine(
                    name = it.name.trim(),
                    quantity = Bn.parseAmount(it.quantity),
                    unit = it.unit,
                    price = Bn.parseAmount(it.price)?.let(Poisha::ofTaka),
                )
            }
        } else emptyList()
        return EntryDraft(
            type = type,
            customer = if (type == EntryType.EXPENSE) null else customer,
            items = lines,
            total = total,
            paid = paidAmount,
            note = note?.takeIf { it.isNotBlank() },
            uncertain = emptySet(),
            transcript = transcript,
        )
    }

    companion object {
        /** The stored/spoken total as an override, only when it disagrees with the priced items. */
        private fun overrideFor(total: Poisha, items: List<ItemLine>): String? {
            val sum = items.sumOf { it.price?.value ?: 0L }
            return if (sum > 0 && total.value > 0 && total.value != sum) Bn.editable(total) else null
        }

        fun from(draft: EntryDraft): ReviewState {
            val existingId = (draft.customer as? CustomerRef.Existing)?.id
            val items = draft.items.mapIndexed { i, it ->
                ItemEdit(
                    key = i.toLong(),
                    name = it.name,
                    quantity = it.quantity?.let(Bn::qty).orEmpty(),
                    unit = it.unit,
                    price = it.price?.let(Bn::editable).orEmpty(),
                )
            }
            return ReviewState(
                type = draft.type,
                customerName = draft.customer?.name.orEmpty(),
                matchedCustomerId = existingId,
                items = items,
                // Also kept when items exist: it covers a spoken total whose items have no prices.
                amount = if (draft.total.value > 0) Bn.editable(draft.total) else "",
                paid = if (draft.type == EntryType.CREDIT_SALE && draft.paid.value > 0) Bn.editable(draft.paid) else "",
                note = draft.note,
                transcript = draft.transcript,
                uncertain = draft.uncertain,
                totalOverride = overrideFor(draft.total, draft.items),
                parsed = draft,
            )
        }

        fun blank(type: EntryType = EntryType.CASH_SALE) = ReviewState(
            type = type, customerName = "", matchedCustomerId = null,
            items = listOf(ItemEdit(0, "", "", null, "")), amount = "", paid = "",
            note = null, transcript = "", uncertain = emptySet(),
        )

        /** Reopens a saved entry for correction. Item-level history isn't tracked, only totals/note/type. */
        fun forEdit(entry: LedgerEntry, history: List<EntryHistorySnapshot>): ReviewState {
            val items = entry.items.mapIndexed { i, it ->
                ItemEdit(
                    key = i.toLong(),
                    name = it.name,
                    quantity = it.quantity?.let(Bn::qty).orEmpty(),
                    unit = it.unit,
                    price = it.price?.let(Bn::editable).orEmpty(),
                )
            }
            return ReviewState(
                type = entry.type,
                customerName = entry.customer?.name.orEmpty(),
                matchedCustomerId = entry.customer?.id,
                items = items.ifEmpty { listOf(ItemEdit(0, "", "", null, "")) },
                amount = if (entry.total.value > 0) Bn.editable(entry.total) else "",
                paid = if (entry.type == EntryType.CREDIT_SALE && entry.paid.value > 0) Bn.editable(entry.paid) else "",
                note = entry.note,
                transcript = entry.transcript,
                uncertain = emptySet(),
                editingEntryId = entry.id,
                history = history,
                totalOverride = overrideFor(entry.total, entry.items),
            )
        }
    }
}
