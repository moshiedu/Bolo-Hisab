package com.bolohisab.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerParserTest {

    private val customers = listOf(
        KnownCustomer(1, "রহিম"),
        KnownCustomer(2, "আব্দুল করিম"),
        KnownCustomer(3, "মিলি"),
        KnownCustomer(4, "জাফর"),
    )
    private val parser = LedgerParser(customers)

    private fun draft(text: String): EntryDraft {
        val r = parser.parse(text)
        assertTrue("expected an entry for \"$text\" but got $r", r is ParseResult.Entry)
        return (r as ParseResult.Entry).draft
    }

    private fun taka(v: Long) = Poisha(v * 100)

    @Test fun multiItemCreditSaleWithPartPayment() {
        val d = draft("রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, ১০০ দিয়েছে")
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertEquals(1L, (d.customer as CustomerRef.Existing).id)
        assertEquals(2, d.items.size)
        assertEquals(ItemLine("চাল", 2.0, QuantityUnit.KG, taka(120)), d.items[0])
        assertEquals(ItemLine("তেল", 1.0, QuantityUnit.LITRE, taka(200)), d.items[1])
        assertEquals(taka(320), d.total)
        assertEquals(taka(100), d.paid)
        assertEquals(taka(220), d.due)
        assertEquals(taka(220), d.balanceDelta)
        assertTrue(d.uncertain.isEmpty())
    }

    @Test fun paymentReceivedMatchesPartOfName() {
        val d = draft("করিম ভাই ৫০০ টাকা জমা দিয়েছে")
        assertEquals(EntryType.PAYMENT_RECEIVED, d.type)
        assertEquals(2L, (d.customer as CustomerRef.Existing).id)
        assertEquals(taka(500), d.paid)
        assertEquals(taka(-500), d.balanceDelta)
    }

    @Test fun spokenAmountCashSale() {
        val d = draft("সাড়ে তিনশো টাকার চিনি বিক্রি")
        assertEquals(EntryType.CASH_SALE, d.type)
        assertEquals(null, d.customer)
        assertEquals(listOf(ItemLine("চিনি", null, null, taka(350))), d.items)
        assertEquals(taka(350), d.paid)
    }

    @Test fun newCustomerWithCaseEndingAndGaveVerb() {
        val d = draft("দেড় কেজি ডাল একশো পঁয়ত্রিশ টাকা বাকিতে দিলাম জামালকে")
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertEquals(CustomerRef.New("জামাল"), d.customer)
        assertEquals(ItemLine("ডাল", 1.5, QuantityUnit.KG, taka(135)), d.items.single())
        assertEquals(Poisha.ZERO, d.paid)
        assertTrue(Field.CUSTOMER in d.uncertain)
    }

    @Test fun expense() {
        val d = draft("দোকান ভাড়া পাঁচ হাজার টাকা খরচ")
        assertEquals(EntryType.EXPENSE, d.type)
        assertEquals(taka(5000), d.total)
        assertEquals("দোকান ভাড়া", d.note)
        assertEquals(Poisha.ZERO, d.balanceDelta)
    }

    @Test fun creditAmountWithoutItems() {
        val d = draft("মিলির কাছে আড়াইশো টাকা বাকি")
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertEquals(3L, (d.customer as CustomerRef.Existing).id)
        assertEquals(taka(250), d.total)
        assertEquals(taka(250), d.balanceDelta)
    }

    @Test fun haliUnit() {
        val d = draft("এক হালি ডিম ৫০ টাকা")
        assertEquals(EntryType.CASH_SALE, d.type)
        assertEquals(ItemLine("ডিম", 1.0, QuantityUnit.HALI, taka(50)), d.items.single())
    }

    @Test fun classifierPiecesAndCash() {
        val d = draft("রহিম ৩টা সাবান ১৫০ টাকা নগদ")
        assertEquals(EntryType.CASH_SALE, d.type)
        assertEquals(ItemLine("সাবান", 3.0, QuantityUnit.PIECE, taka(150)), d.items.single())
        assertEquals(taka(150), d.paid)
    }

    @Test fun explicitDueIsChecked() {
        val d = draft("রহিম ২ কেজি চাল ১২০ টাকা, ১০০ দিয়েছে বাকি ২০")
        assertEquals(taka(20), d.due)
        assertTrue(Field.PAID !in d.uncertain)

        val wrong = draft("রহিম ২ কেজি চাল ১২০ টাকা, ১০০ দিয়েছে বাকি ৫০")
        assertTrue(Field.PAID in wrong.uncertain)
    }

    @Test fun nameEndingInRIsNotStripped() {
        val d = draft("জাফর ৩০০ টাকা শোধ করেছে")
        assertEquals(EntryType.PAYMENT_RECEIVED, d.type)
        assertEquals(4L, (d.customer as CustomerRef.Existing).id)
    }

    @Test fun possessiveOfKnownCustomer() {
        val d = draft("রহিমের বাকি ২০০ টাকা")
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertEquals(1L, (d.customer as CustomerRef.Existing).id)
        assertEquals(taka(200), d.total)
    }

    @Test fun paymentAgainstDueIsPaymentNotCredit() {
        val d = draft("রহিম বাকির ৫০০ টাকা শোধ করেছে")
        assertEquals(EntryType.PAYMENT_RECEIVED, d.type)
        assertEquals(taka(-500), d.balanceDelta)
    }

    @Test fun priceInSeparateClauseAttachesToItem() {
        val d = draft("২ কেজি চাল, ১২০ টাকা")
        assertEquals(ItemLine("চাল", 2.0, QuantityUnit.KG, taka(120)), d.items.single())
    }

    @Test fun barePriceWithoutTaka() {
        val d = draft("ডিম ১২টা ১২০")
        assertEquals(ItemLine("ডিম", 12.0, QuantityUnit.PIECE, taka(120)), d.items.single())
    }

    @Test fun itemWithoutCustomerAndNoPriceIsFlagged() {
        val d = draft("২ কেজি চাল")
        assertTrue(Field.ITEMS in d.uncertain)
        assertTrue(Field.TOTAL in d.uncertain)
    }

    @Test fun unknownFirstWordFollowedByNumberIsNewCustomer() {
        val d = draft("সুমন ৩০০ টাকা বাকি")
        assertEquals(CustomerRef.New("সুমন"), d.customer)
        assertEquals(EntryType.CREDIT_SALE, d.type)
    }

    @Test fun genericCustomerWordIsNotTakenAsAName() {
        // "কাস্টমার" (a customer) misheard by ASR as "কাস্টমা" — must not become a customer named that.
        val d = draft("কাস্টমা ২ লিটার তেল ২০০ টাকা")
        assertEquals(null, d.customer)
        assertEquals(ItemLine("তেল", 2.0, QuantityUnit.LITRE, taka(200)), d.items.single())

        val full = draft("কাস্টমার ২ লিটার তেল ২০০ টাকা")
        assertEquals(null, full.customer)
    }

    @Test fun customerWithoutPaymentInfoIsFlaggedAsTypeGuess() {
        val d = draft("রহিম ২ কেজি চাল ১২০ টাকা")
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertTrue(Field.TYPE in d.uncertain)
    }

    @Test fun honorificWithCaseEndingAndOwedVerb() {
        val d = draft("করিম ভাইয়ের কাছে এক হাজার দুইশো পঞ্চাশ টাকা পাব")
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertEquals(2L, (d.customer as CustomerRef.Existing).id)
        assertTrue(d.items.isEmpty())
        assertEquals(taka(1250), d.total)
        assertTrue(Field.TYPE !in d.uncertain)
    }

    @Test fun creditPriceOfLastItemIsNotCountedTwice() {
        val d = draft("মিলি আধা কেজি পেঁয়াজ চল্লিশ টাকা আর এক প্যাকেট বিস্কুট বিশ টাকা বাকি নিল")
        assertEquals(3L, (d.customer as CustomerRef.Existing).id)
        assertEquals(taka(60), d.total)
        assertEquals(Poisha.ZERO, d.paid)
        assertEquals(ItemLine("বিস্কুট", 1.0, QuantityUnit.PACKET, taka(20)), d.items[1])
    }

    @Test fun nameThatLooksLikeAUnit() {
        val d = draft("মিলি দুইশো টাকা দিয়ে গেছে")
        assertEquals(EntryType.PAYMENT_RECEIVED, d.type)
        assertEquals(3L, (d.customer as CustomerRef.Existing).id)
    }

    @Test fun spokenTotalPricesTheOnlyUnpricedItem() {
        val d = draft("রহিমকে পাঁচ কেজি চাল দিলাম, মোট ৩০০ টাকা, দেড়শো দিয়েছে")
        assertEquals(taka(300), d.items.single().price)
        assertEquals(taka(150), d.due)
        assertTrue(d.uncertain.isEmpty())
    }

    @Test fun queries() {
        val due = parser.parse("রহিমের কত বাকি?") as ParseResult.Query
        val q = due.query as LedgerQuery.CustomerDue
        assertEquals(1L, (q.customer as CustomerRef.Existing).id)

        val sales = parser.parse("আজ মোট বিক্রি কত") as ParseResult.Query
        assertEquals(LedgerQuery.Sales(Period.TODAY), sales.query)

        val month = parser.parse("এই মাসে কত বিক্রি হলো") as ParseResult.Query
        assertEquals(LedgerQuery.Sales(Period.THIS_MONTH), month.query)

        val top = parser.parse("সবচেয়ে বেশি বাকি কার") as ParseResult.Query
        assertEquals(LedgerQuery.TopDebtors, top.query)
    }

    @Test fun noise() {
        assertTrue(parser.parse("") is ParseResult.Unrecognized)
        assertTrue(parser.parse("হ্যালো কেমন আছেন") is ParseResult.Unrecognized)
    }

    @Test fun spokenTotalBelowItemPricesIsKeptAndFlagged() {
        val d = draft("রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, মোট ৩০০ টাকা, ১০০ দিয়েছে")
        assertEquals(taka(320), Poisha(d.items.sumOf { it.price!!.value }))
        assertEquals(taka(300), d.total)
        assertEquals(taka(200), d.due)
        assertTrue(Field.TOTAL in d.uncertain)
    }

    @Test fun regionalWordForTakaParsesLikeTheStandard() {
        val chattogram = draft("রহিম ৫০০ টেঁয়া বাকি")
        assertEquals(EntryType.CREDIT_SALE, chattogram.type)
        assertEquals(taka(500), chattogram.total)
        assertEquals(taka(500), draft("রহিম ৫০০ টেখা বাকি").total)
    }

    @Test fun colloquialHowMuchIsAQuestion() {
        val q = parser.parse("রহিমের কয় টাকা বাকি")
        assertTrue(q is ParseResult.Query)
        assertEquals(1L, (((q as ParseResult.Query).query as LedgerQuery.CustomerDue).customer as CustomerRef.Existing).id)
    }

    @Test fun separatePossessiveAfterANameIsStillADueQuestion() {
        // The typing help offers "এর কত বাকি" after a name, written as its own word.
        val q = parser.parse("রহিম এর কত বাকি")
        assertTrue("got $q", q is ParseResult.Query)
        assertEquals(1L, (((q as ParseResult.Query).query as LedgerQuery.CustomerDue).customer as CustomerRef.Existing).id)
    }

    @Test fun separateKeAfterANameStillRecordsCredit() {
        val d = draft("রহিম কে ৫০০ টাকা বাকি দিলাম")
        assertEquals(EntryType.CREDIT_SALE, d.type)
        assertEquals(1L, (d.customer as CustomerRef.Existing).id)
        assertEquals(taka(500), d.total)
    }
}
