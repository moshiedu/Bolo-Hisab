package com.bolohisab.nlu.typing

import com.bolohisab.nlu.Corrections
import com.bolohisab.nlu.CustomerRef
import com.bolohisab.nlu.KnownCustomer
import com.bolohisab.nlu.LedgerParser
import com.bolohisab.nlu.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class LearningTest {

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    private fun suggester(memory: TypingMemory = TypingMemory.EMPTY, customers: List<String> = listOf("রহিম")) =
        PhoneticSuggester(TypingDictionary.forShop(customers = customers, memory = memory))

    private fun texts(s: Suggestions?) = s?.items?.map { nfc(it.text) }.orEmpty()

    // ------------------------------------------------------------------ built-in phrasebook

    @Test fun builtInVocabularyLoads() {
        val v = BuiltInVocabulary.data
        assertTrue(v.words.size > 150)
        assertTrue(v.phrases.size > 20)
        assertTrue(v.next.any { it.first == TypingMemory.NUMBER })
    }

    @Test fun afterANumberUnitsAndTakaAreOffered() {
        val text = "রহিম ২ "
        val got = texts(suggester().suggest(text, text.length))
        assertTrue(got.toString(), nfc("কেজি") in got)
        assertTrue(got.toString(), nfc("টাকা") in got)
    }

    @Test fun afterAUnitTheUsualGoodsAreOffered() {
        val text = "২ কেজি "
        assertTrue(nfc("চাল") in texts(suggester().suggest(text, text.length)))
    }

    @Test fun afterACustomerNameTheUsualContinuationIsOffered() {
        val text = "রহিম "
        assertTrue(nfc("এর কত বাকি") in texts(suggester().suggest(text, text.length)))
    }

    // ---------------------------------------------------------------------- learned typing

    @Test fun aPastPickForASpellingComesFirst() {
        val memory = TypingMemory(choices = listOf(LearnedChoice("bhai", "ভাইয়া", 3)))
        val s = suggester(memory).suggest("bhai", 4)!!
        assertEquals(nfc("ভাইয়া"), nfc(s.items.first().text))
        assertEquals(nfc("ভাইয়া"), nfc(s.commitOnSpace!!.text))
    }

    @Test fun aSpellingKeptInEnglishStaysEnglishOnSpace() {
        val memory = TypingMemory(choices = listOf(LearnedChoice("ruchi", "ruchi", 2)))
        val s = suggester(memory).suggest("ruchi", 5)!!
        assertEquals(Suggestion("ruchi", SuggestionKind.ORIGINAL), s.items.first())
        assertNull(s.commitOnSpace)
    }

    @Test fun aWordUsedInEntriesJoinsTheDictionary() {
        val memory = TypingMemory(words = listOf(LearnedWord("রূপচাঁদা", 3)))
        assertEquals(nfc("রূপচাঁদা"), nfc(suggester(memory).suggest("rupchanda", 9)!!.items.first().text))
    }

    @Test fun aLearnedPairIsPredictedNext() {
        val memory = TypingMemory(pairs = listOf(LearnedPair("রহিম", "ভাইয়ের", 4)))
        val text = "রহিম "
        assertEquals(nfc("ভাইয়ের"), texts(suggester(memory).suggest(text, text.length)).first())
    }

    // ----------------------------------------------------------------------- corrections

    private fun parse(text: String, customers: List<KnownCustomer> = emptyList(), fixes: Map<String, String> = emptyMap()) =
        (LedgerParser(customers, corrections = fixes).parse(text) as ParseResult.Entry).draft

    @Test fun aMisheardNameFixedOnTheCardIsLearnedAndApplied() {
        val heard = parse("রোহিম ৫০০ টাকা বাকি")
        val saved = heard.copy(customer = CustomerRef.New("রহিম"))
        val learned = Corrections.learn(heard, saved, customers = emptyList(), products = emptyList())
        assertEquals(listOf(nfc("রোহিম") to nfc("রহিম")), learned.map { nfc(it.first) to nfc(it.second) })

        val next = parse("রোহিমের ৩০০ টাকা বাকি", listOf(KnownCustomer(1, "রহিম")), learned.toMap())
        assertEquals(1L, (next.customer as CustomerRef.Existing).id)
    }

    @Test fun choosingADifferentPersonIsNotAMishearing() {
        val heard = parse("জামাল ৫০০ টাকা বাকি")
        val saved = heard.copy(customer = CustomerRef.New("করিম"))
        assertTrue(Corrections.learn(heard, saved, emptyList(), emptyList()).isEmpty())
    }

    @Test fun grammarUnitAndNumberWordsAreNeverLearned() {
        listOf("টাকা", "বাকি", "কেজি", "চার", "দেড়শো", "দিয়েছে").forEach {
            assertTrue("$it must be protected", Corrections.isProtected(it))
        }
        assertFalse(Corrections.isProtected("রোহিম"))
    }

    @Test fun anExistingCustomersNameIsNeverRemapped() {
        // "রহিম" heard, but the card picked "রহিমা": that's another customer, not a fix.
        val heard = parse("রহিম ৫০০ টাকা বাকি")
        val saved = heard.copy(customer = CustomerRef.New("রহিমা"))
        assertTrue(Corrections.learn(heard, saved, listOf(KnownCustomer(1, "রহিম")), emptyList()).isEmpty())
    }

    // ---------------------------------------------------------------------------- usage

    @Test fun usageCollectsWordsAndPairs() {
        val draft = parse("রহিম ২ কেজি চাল ১২০ টাকা")
        val usage = UsageLearner.from(draft)
        listOf("রহিম", "কেজি", "চাল", "টাকা").forEach { assertTrue(it, nfc(it) in usage.words.map(::nfc)) }
        val pairs = usage.pairs.map { nfc(it.first) to nfc(it.second) }
        assertTrue(pairs.toString(), (TypingMemory.NUMBER to nfc("কেজি")) in pairs)
        assertTrue(pairs.toString(), (nfc("কেজি") to nfc("চাল")) in pairs)
        assertTrue(pairs.toString(), (TypingMemory.NUMBER to nfc("টাকা")) in pairs)
    }

    @Test fun goodsAreNeverLearnedAsMishearings() {
        // ডাল and চাল sound alike; one fix must not turn every later ডাল into চাল.
        assertTrue(Corrections.isProtected("ডাল"))
        val heard = parse("রহিম ১ কেজি ডাল ১২০ টাকা")
        val saved = heard.copy(items = heard.items.map { it.copy(name = "চাল") })
        assertTrue(Corrections.learn(heard, saved, listOf(KnownCustomer(1, "রহিম")), emptyList()).isEmpty())
    }

    @Test fun aFixStopsOnceTheHeardNameBecomesARealCustomer() {
        val fixes = mapOf("রোহিম" to "রহিম")
        val customers = listOf(KnownCustomer(1, "রহিম"), KnownCustomer(2, "রোহিম"))
        val d = parse("রোহিমের ৫০০ টাকা বাকি", customers, fixes)
        assertEquals(2L, (d.customer as CustomerRef.Existing).id)
    }
}
