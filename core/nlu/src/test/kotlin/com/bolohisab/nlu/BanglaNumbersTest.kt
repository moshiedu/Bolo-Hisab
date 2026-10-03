package com.bolohisab.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BanglaNumbersTest {

    private fun num(text: String) = BanglaNumbers.firstNumber(text)

    private fun numbers(text: String) =
        BanglaNumbers.parse(BanglaText.tokenize(text)).filterIsInstance<Token.Num>().map { it.value }

    @Test fun bengaliDigits() = assertEquals(120.0, num("১২০ টাকা"))
    @Test fun gluedDigits() = assertEquals(listOf(120.0), numbers("১২০টাকা"))
    @Test fun thousandsSeparator() = assertEquals(1200.0, num("১,২০০ টাকা"))
    @Test fun decimal() = assertEquals(1.5, num("1.5 কেজি"))

    @Test fun singleWords() {
        assertEquals(5.0, num("পাঁচ"))
        assertEquals(35.0, num("পঁয়ত্রিশ"))
        assertEquals(99.0, num("নিরানব্বই"))
        assertEquals(20.0, num("কুড়ি"))
    }

    @Test fun hundredsCompound() {
        assertEquals(300.0, num("তিনশো"))
        assertEquals(100.0, num("একশ"))
        assertEquals(200.0, num("দুশো"))
        assertEquals(600.0, num("ছশো"))
        assertEquals(500.0, num("পাঁচশত"))
    }

    @Test fun hundredsSplit() = assertEquals(120.0, num("একশো বিশ"))

    @Test fun fractions() {
        assertEquals(1.5, num("দেড় কেজি"))
        assertEquals(2.5, num("আড়াই কেজি"))
        assertEquals(150.0, num("দেড়শো"))
        assertEquals(250.0, num("আড়াইশো"))
        assertEquals(0.5, num("আধা কেজি"))
    }

    @Test fun modifiers() {
        assertEquals(350.0, num("সাড়ে তিনশো"))
        assertEquals(3.5, num("সাড়ে তিন কেজি"))
        assertEquals(1.75, num("পৌনে দুই কেজি"))
        assertEquals(1.25, num("সোয়া এক কেজি"))
        assertEquals(5500.0, num("সাড়ে পাঁচ হাজার"))
    }

    @Test fun bigNumbers() {
        assertEquals(1250.0, num("এক হাজার দুইশো পঞ্চাশ"))
        assertEquals(225000.0, num("দুই লাখ পঁচিশ হাজার"))
        assertEquals(1000.0, num("হাজার"))
        assertEquals(2000.0, num("২ হাজার"))
    }

    @Test fun adjacentNumbersStaySeparate() =
        assertEquals(listOf(2.0, 120.0), numbers("দুই কেজি একশো বিশ টাকা"))

    @Test fun classifierSplitsIntoPieces() {
        val tokens = BanglaNumbers.parse(BanglaText.tokenize("দুইটা ডিম"))
        assertEquals(Token.Num(2.0, "দুইটা"), tokens[0])
        assertEquals(Token.Word(BanglaText.key("টা")), tokens[1])
    }

    @Test fun nonNumberWords() {
        assertNull(num("দশটার মধ্যে আসবে না"))
        assertNull(num("চাল ডাল"))
        assertNull(num("আরেক বার"))
    }

    @Test fun precomposedAndDecomposedMatch() {
        // "ছয়" with precomposed য় (U+09DF) and with য + nukta.
        assertEquals(6.0, num("ছয়"))
        assertEquals(6.0, num("ছয়"))
    }
}
