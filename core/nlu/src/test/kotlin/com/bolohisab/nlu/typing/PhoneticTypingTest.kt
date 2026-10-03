package com.bolohisab.nlu.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class PhoneticTypingTest {

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    private fun avro(latin: String) = nfc(AvroPhonetic.transliterate(latin))

    private val suggester = PhoneticSuggester(
        TypingDictionary.forShop(customers = listOf("রহিম", "আব্দুল করিম"), products = listOf("মিনিকেট চাল")),
    )

    private fun suggest(text: String, context: TypingContext = TypingContext.SENTENCE) =
        suggester.suggest(text, text.length, context)

    private fun top(text: String, context: TypingContext = TypingContext.SENTENCE): String {
        val s = suggest(text, context)
        assertNotNull("no suggestions for \"$text\"", s)
        return nfc(s!!.items.first().text)
    }

    // ------------------------------------------------------------ transliteration

    @Test fun avroBasics() {
        assertEquals(nfc("আমার"), avro("amar"))
        assertEquals(nfc("কলম"), avro("kolom"))
        assertEquals(nfc("বাংলা"), avro("bangla"))
        assertEquals(nfc("দোকান"), avro("dOkan"))
        assertEquals(nfc("বাড়ি"), avro("baRi"))
        assertEquals(nfc("ক্রম"), avro("krom"))
        assertEquals(nfc("ব্যাগ"), avro("byag"))
    }

    @Test fun autoCapitalisedFirstLetterIsIgnored() {
        assertEquals(avro("chal"), avro("Chal"))
        // A deliberate capital mid-word is Avro syntax and kept.
        assertEquals(nfc("কাট"), avro("kaT"))
    }

    // ---------------------------------------------------------------- suggestions

    @Test fun looseBanglishReachesTheLedgerWord() {
        assertEquals(nfc("চাল"), top("chal"))
        assertEquals(nfc("টাকা"), top("taka"))
        assertEquals(nfc("দোকান"), top("dokan"))
        assertEquals(nfc("বাকি"), top("baki"))
        assertEquals(nfc("চিনি"), top("chini"))
        assertEquals(nfc("দিয়েছে"), top("diyeche"))
    }

    @Test fun englishWordsMapToBangla() {
        assertEquals(nfc("কেজি"), top("kg"))
        assertEquals(nfc("তেল"), top("oil"))
        assertEquals(nfc("বাকি"), top("due"))
    }

    @Test fun shopCustomersWinForNames() {
        assertEquals(nfc("রহিম"), top("rahim", TypingContext.CUSTOMER))
        assertEquals(nfc("রহিম"), top("rohim", TypingContext.CUSTOMER))
        assertEquals(nfc("রহিম"), top("rahim"))
    }

    @Test fun unknownWordFallsBackToAvroThenTheLatin() {
        val s = suggest("jamal")!!
        assertEquals(SuggestionKind.TRANSLITERATION, s.items.first().kind)
        assertEquals(nfc("জামাল"), nfc(s.items.first().text))
        assertEquals(Suggestion("jamal", SuggestionKind.ORIGINAL), s.items.last())
    }

    @Test fun spaceCommitsWholeWordsNotCompletions() {
        // "ch" is the start of many words; space must not turn it into one of them.
        val s = suggest("ch")!!
        val commit = s.commitOnSpace
        assertTrue(commit == null || commit.kind != SuggestionKind.COMPLETION)
        assertEquals(nfc("চাল"), nfc(suggest("chal")!!.commitOnSpace!!.text))
    }

    @Test fun firstWordOffersTheWholeSentence() {
        val s = suggest("aj")!!
        assertEquals(nfc("আজ"), nfc(s.items.first().text))
        assertTrue(s.items.any { nfc(it.text) == nfc("আজ মোট বিক্রি কত") })
    }

    @Test fun afterASpaceTheSentenceContinues() {
        val s = suggest("আজ ")!!
        assertTrue(s.items.any { nfc(it.text) == nfc("মোট বিক্রি কত") })
    }

    @Test fun banglaScriptIsCompletedToo() {
        val s = suggest("চি")!!
        assertTrue(s.items.any { nfc(it.text) == nfc("চিনি") })
    }

    @Test fun productNamesAreCompletedInItemFields() {
        val s = suggest("miniket", TypingContext.ITEM)
        assertNotNull(s)
        assertTrue(s!!.items.any { nfc(it.text) == nfc("মিনিকেট চাল") })
    }

    @Test fun numbersAndPunctuationAreNotWords() {
        assertNull(suggest("120"))
        assertNull(suggest("chal,"))
    }

    @Test fun suggestsForTheWordAtTheCursorAfterDigits() {
        val s = suggest("120tk")!!
        assertEquals(3, s.start)
        assertEquals(nfc("টাকা"), nfc(s.items.first().text))
    }

    // ----------------------------------------------------------------------- apply

    @Test fun applyReplacesTheWordAndAddsASpace() {
        val text = "rahim 2 kg chal"
        val s = suggester.suggest(text, text.length)!!
        val (out, cursor) = PhoneticSuggester.apply(text, s, Suggestion("চাল", SuggestionKind.WORD))
        assertEquals("rahim 2 kg চাল ", out)
        assertEquals(out.length, cursor)
    }

    @Test fun applyInTheMiddleKeepsTheExistingSpace() {
        val text = "chal 120"
        val s = suggester.suggest(text, 4)!!
        val (out, cursor) = PhoneticSuggester.apply(text, s, Suggestion("চাল", SuggestionKind.WORD))
        assertEquals("চাল 120", out)
        assertEquals(4, cursor)
    }

    @Test fun aPartOfAMultiWordNameBringsUpTheCustomer() {
        assertEquals(nfc("আব্দুল করিম"), top("korim", TypingContext.CUSTOMER))
        assertTrue(suggest("karim")!!.items.any { nfc(it.text) == nfc("আব্দুল করিম") })
    }

    @Test fun commonWordsNeedMoreThanMatchingConsonants() {
        // টাকা and থেকে share consonants; only the real word may be offered for "taka".
        assertTrue(suggest("taka")!!.items.none { nfc(it.text) == nfc("থেকে") })
    }
}
