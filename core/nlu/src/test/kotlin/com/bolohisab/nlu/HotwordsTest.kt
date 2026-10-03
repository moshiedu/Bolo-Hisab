package com.bolohisab.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotwordsTest {

    @Test fun shopNamesComeFirstThenTheSharedLexicon() {
        val words = Hotwords.build(customers = listOf("রহিম", "আব্দুল করিম"), products = listOf("মিনিকেট চাল"))
        assertEquals(listOf("রহিম", "আব্দুল করিম", "মিনিকেট চাল"), words.take(3))
        assertTrue(BanglaText.key("কেজি") in words.map(BanglaText::key))
        // A goods word that only words.csv knows.
        assertTrue(BanglaText.key("সিমেন্ট") in words.map(BanglaText::key))
    }

    @Test fun duplicatesBlanksAndLatinAreDroppedAndTheListIsCapped() {
        val words = Hotwords.build(customers = listOf("রহিম", " রহিম ", "", "Rahim"), limit = 5)
        assertEquals(1, words.count { BanglaText.key(it) == BanglaText.key("রহিম") })
        assertTrue(words.none { it == "Rahim" || it.isBlank() })
        assertEquals(5, words.size)
    }
}
