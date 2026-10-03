package com.bolohisab.ui

import com.bolohisab.nlu.CustomerRef
import com.bolohisab.nlu.EntryType
import com.bolohisab.nlu.Field
import com.bolohisab.nlu.KnownCustomer
import com.bolohisab.nlu.LedgerParser
import com.bolohisab.nlu.ParseResult
import com.bolohisab.nlu.Poisha
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.record.ReviewProblem
import com.bolohisab.ui.record.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewStateTest {

    private val customers = listOf(KnownCustomer(1, "রহিম"), KnownCustomer(2, "আব্দুল করিম"))

    private fun review(text: String): ReviewState {
        val r = LedgerParser(customers).parse(text) as ParseResult.Entry
        return ReviewState.from(r.draft)
    }

    private fun taka(v: Long) = Poisha(v * 100)

    @Test fun roundTripKeepsTheParsedEntry() {
        val r = review("রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, ১০০ দিয়েছে")
        assertEquals(taka(320), r.total)
        assertEquals(taka(220), r.due)
        assertTrue(r.problems.isEmpty())
        val d = r.toDraft()
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertEquals(CustomerRef.Existing(1, "রহিম", 1.0), d.customer)
        assertEquals(taka(220), d.balanceDelta)
        assertEquals(2, d.items.size)
    }

    @Test fun editingAPriceUpdatesTotals() {
        val r = review("রহিম ২ কেজি চাল ১২০ টাকা, ১০০ দিয়েছে")
        val edited = r.copy(items = r.items.map { it.copy(price = "১৫০") })
        assertEquals(taka(150), edited.total)
        assertEquals(taka(50), edited.due)
    }

    @Test fun switchingToCashSaleMakesItFullyPaid() {
        val r = review("রহিম ২ কেজি চাল ১২০ টাকা বাকি").withType(EntryType.CASH_SALE)
        assertEquals(taka(120), r.paidAmount)
        assertEquals(Poisha.ZERO, r.toDraft().balanceDelta)
        assertTrue(Field.TYPE !in r.uncertain)
    }

    @Test fun creditNeedsACustomer() {
        val r = ReviewState.blank(EntryType.CREDIT_SALE).copy(amount = "200")
        assertTrue(ReviewProblem.NEED_CUSTOMER in r.problems)
        val named = r.withCustomer("সুমন", customers)
        assertTrue(named.problems.isEmpty())
        assertEquals(CustomerRef.New("সুমন"), named.toDraft().customer)
    }

    @Test fun typingAnExistingNameLinksTheCustomer() {
        val r = ReviewState.blank(EntryType.PAYMENT_RECEIVED).copy(amount = "500").withCustomer("আব্দুল করিম", customers)
        assertEquals(2L, r.matchedCustomerId)
        assertEquals(taka(-500), r.toDraft().balanceDelta)
    }

    @Test fun paidMoreThanTotalIsRejected() {
        val r = review("রহিম ২ কেজি চাল ১২০ টাকা বাকি").copy(paid = "500")
        assertTrue(ReviewProblem.PAID_TOO_MUCH in r.problems)
    }

    @Test fun emptyAmountIsRejected() {
        assertTrue(ReviewProblem.NEED_AMOUNT in ReviewState.blank().problems)
    }

    @Test fun expenseDropsTheCustomer() {
        val r = ReviewState.blank(EntryType.EXPENSE).copy(amount = "3000", note = "বিদ্যুৎ বিল", customerName = "রহিম")
        val d = r.toDraft()
        assertEquals(null, d.customer)
        assertEquals(taka(3000), d.total)
    }

    @Test fun bengaliFormatting() {
        assertEquals("৳১,২৫০", Bn.taka(taka(1250)))
        assertEquals("৳১২,৫০,০০০", Bn.taka(taka(1_250_000)))
        assertEquals("৳৩৫.৫০", Bn.taka(Poisha(3550)))
        assertEquals("−৳৫০০", Bn.taka(taka(-500)))
        assertEquals("১.৫", Bn.qty(1.5))
        assertEquals("৩", Bn.qty(3.0))
        assertEquals(1250.0, Bn.parseAmount("১,২৫০"))
        assertEquals("35.5", Bn.editable(Poisha(3550)))
    }

    @Test fun spokenDiscountedTotalIsKeptOverItemSum() {
        val r = review("রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, মোট ৩০০ টাকা, ১০০ দিয়েছে")
        assertEquals(taka(320), r.itemSum)
        assertEquals(taka(300), r.total)
        assertEquals(taka(200), r.due)
        assertEquals(taka(300), r.toDraft().total)
        assertEquals(taka(200), r.toDraft().balanceDelta)
    }

    @Test fun clearingTheOverrideFallsBackToItemSum() {
        val r = review("রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, মোট ৩০০ টাকা, ১০০ দিয়েছে")
            .copy(totalOverride = null)
        assertEquals(taka(320), r.total)
    }

    @Test fun matchingSpokenTotalSetsNoOverride() {
        val r = review("রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, মোট ৩২০ টাকা")
        assertEquals(null, r.totalOverride)
        assertEquals(taka(320), r.total)
    }

    @Test fun extraSpacesStillMatchTheExistingCustomer() {
        val r = ReviewState.blank(EntryType.CREDIT_SALE).withCustomer("  রহিম ", customers)
        assertEquals(1L, r.matchedCustomerId)
    }

    @Test fun aNearlySameNameIsOfferedBeforeCreatingANewCustomer() {
        val r = ReviewState.blank(EntryType.CREDIT_SALE).withCustomer("রহীম", customers)
        assertEquals(null, r.matchedCustomerId)
        assertEquals(KnownCustomer(1, "রহিম"), r.nearMatch(customers))
    }
}
