package com.bolohisab.nlu

import kotlin.math.roundToLong

/** Money is always held as an integer count of poisha (1 taka = 100 poisha). */
@JvmInline
value class Poisha(val value: Long) : Comparable<Poisha> {
    operator fun plus(other: Poisha) = Poisha(value + other.value)
    operator fun minus(other: Poisha) = Poisha(value - other.value)
    override fun compareTo(other: Poisha) = value.compareTo(other.value)
    val taka: Double get() = value / 100.0

    companion object {
        val ZERO = Poisha(0)
        fun ofTaka(taka: Double) = Poisha((taka * 100).roundToLong())
    }
}

enum class EntryType {
    /** Goods sold and fully paid. No change to any customer balance. */
    CASH_SALE,

    /** Goods (or an amount) given on credit: the customer's due goes up by total − paid. */
    CREDIT_SALE,

    /** A customer paid back: their due goes down. */
    PAYMENT_RECEIVED,

    /** Shop expense such as rent or wages. */
    EXPENSE,
}

/** Which fields the confirm card should highlight for the shopkeeper to check. */
enum class Field { CUSTOMER, TYPE, ITEMS, TOTAL, PAID, AMOUNT }

data class KnownCustomer(val id: Long, val name: String)

/** A tracked product [ItemMatcher] can resolve a spoken/typed item name to. */
data class KnownItem(val id: Long, val name: String)

sealed interface CustomerRef {
    val name: String

    data class Existing(val id: Long, override val name: String, val score: Double) : CustomerRef
    data class New(override val name: String) : CustomerRef
}

data class ItemLine(
    val name: String,
    val quantity: Double?,
    val unit: QuantityUnit?,
    val price: Poisha?,
)

data class EntryDraft(
    val type: EntryType,
    val customer: CustomerRef?,
    val items: List<ItemLine>,
    val total: Poisha,
    val paid: Poisha,
    val note: String?,
    val uncertain: Set<Field>,
    val transcript: String,
) {
    val due: Poisha get() = if (total > paid) total - paid else Poisha.ZERO

    /** How much this entry changes the customer's outstanding balance. */
    val balanceDelta: Poisha
        get() = when (type) {
            EntryType.CREDIT_SALE -> total - paid
            EntryType.PAYMENT_RECEIVED -> Poisha(-paid.value)
            EntryType.CASH_SALE, EntryType.EXPENSE -> Poisha.ZERO
        }
}

enum class Period { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH }

sealed interface LedgerQuery {
    /** "রহিমের কত বাকি?" */
    data class CustomerDue(val customer: CustomerRef?) : LedgerQuery

    /** "আজ মোট বিক্রি কত?" */
    data class Sales(val period: Period) : LedgerQuery

    /** "সবচেয়ে বেশি বাকি কার?" */
    data object TopDebtors : LedgerQuery
}

sealed interface ParseResult {
    val transcript: String

    data class Entry(val draft: EntryDraft) : ParseResult {
        override val transcript get() = draft.transcript
    }

    data class Query(val query: LedgerQuery, override val transcript: String) : ParseResult
    data class Unrecognized(override val transcript: String) : ParseResult
}
