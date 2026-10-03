package com.bolohisab.nlu

import java.text.Normalizer

/**
 * Text normalisation for Bangla speech output and typed input.
 *
 * - NFC normalisation, so "ো" and "য়" have one canonical code-point sequence
 *   (U+09DF য় and U+09DC ড় are composition exclusions and come out decomposed).
 * - Bengali digits (০–৯) become ASCII digits.
 * - Zero-width joiners are dropped, Latin text is lower-cased.
 * - Digits glued to words are split: "১২০টাকা" -> "120 টাকা", "২কেজি" -> "2 কেজি".
 * - Thousand separators are removed: "1,200" -> "1200".
 */
object BanglaText {

    private val digitThenLetter = Regex("""(\d)([\p{L}\p{M}])""")
    private val letterThenDigit = Regex("""([\p{L}\p{M}])(\d)""")
    private val thousandsComma = Regex("""(\d),(?=\d{3}(\D|$))""")
    private val separators = Regex("""[,।|!?;:\n]""")
    private val noise = Regex("""["'()\[\]{}*_=+/\\-]""")
    private val nonDecimalDot = Regex("""(?<!\d)\.|\.(?!\d)""")
    private val spaces = Regex("""\s+""")

    fun normalize(input: String): String {
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length + 8)
        for (ch in nfc) {
            when (ch) {
                in '০'..'৯' -> sb.append('0' + (ch - '০'))
                '‌', '‍', '﻿' -> Unit
                else -> sb.append(ch.lowercaseChar())
            }
        }
        var s = sb.toString()
        s = thousandsComma.replace(s, "$1")
        s = digitThenLetter.replace(s, "$1 $2")
        s = letterThenDigit.replace(s, "$1 $2")
        return s
    }

    /** Normalises a single lexicon word so dictionary keys match normalised input. */
    fun key(word: String): String = normalize(word).trim()

    /**
     * Splits normalised text into words and clause separators.
     * A separator is returned as the literal token [SEPARATOR].
     */
    fun tokenize(input: String): List<String> {
        val s = normalize(input)
        val cleaned = nonDecimalDot.replace(noise.replace(s, " "), " $SEPARATOR ")
        val marked = separators.replace(cleaned, " $SEPARATOR ")
        return spaces.split(marked.trim()).filter { it.isNotEmpty() }
    }

    const val SEPARATOR = "<sep>"
}
