package com.bolohisab.nlu.typing

import java.text.Normalizer

/**
 * A loose sound key shared by Latin (Banglish) and Bangla text, so "chal", "chaal" and "চাল"
 * all become "cal". Shopkeepers don't type strict Avro: they write "taka" for টাকা (Avro: তাকা),
 * "dokan" for দোকান and "rahim" for রহিম. The key therefore:
 *
 * - merges aspirated/unaspirated and dental/retroflex pairs (ত ট থ ঠ -> t, শ ষ স -> s, চ ছ -> c);
 * - drops "o", because it is usually the unwritten inherent vowel (কলম = "kolom" = k-l-m);
 * - keeps a, i, u, e, which are always written, and collapses doubled letters.
 *
 * [skeleton] goes further and keeps consonants only, for names typed with the "wrong" vowel.
 */
internal object PhoneticKey {

    private val latinDigraphs = listOf(
        "kh" to "k", "gh" to "g", "ch" to "c", "jh" to "j", "th" to "t", "dh" to "d", "ph" to "f",
        "bh" to "b", "sh" to "s", "rh" to "r", "ng" to "N", "aa" to "a", "ee" to "i", "oo" to "u", "ou" to "u",
    )

    private val latinSingles: Map<Char, String> = mapOf(
        'k' to "k", 'q' to "k", 'g' to "g", 'c' to "c", 'j' to "j", 'z' to "j", 't' to "t", 'd' to "d",
        'n' to "n", 'p' to "p", 'f' to "f", 'b' to "b", 'v' to "b", 'm' to "m", 'r' to "r", 'l' to "l",
        's' to "s", 'x' to "ks", 'h' to "h", 'y' to "y", 'a' to "a", 'i' to "i", 'u' to "u", 'e' to "e",
        'o' to "", 'w' to "",
    )

    private val bangla: Map<Char, String> = buildMap {
        "কখ".forEach { put(it, "k") }; "গঘ".forEach { put(it, "g") }; put('ঙ', "N")
        "চছ".forEach { put(it, "c") }; "জঝয".forEach { put(it, "j") }; "ঞণন".forEach { put(it, "n") }
        "টঠতথৎ".forEach { put(it, "t") }; "ডঢদধ".forEach { put(it, "d") }
        put('প', "p"); put('ফ', "f"); "বভ".forEach { put(it, "b") }; put('ম', "m")
        put('র', "r"); put('ল', "l"); "শষস".forEach { put(it, "s") }; put('হ', "h")
        put('ং', "N")
        "আা".forEach { put(it, "a") }; "ইিঈী".forEach { put(it, "i") }; "উুঊূ".forEach { put(it, "u") }
        "এে".forEach { put(it, "e") }; "ঐৈ".forEach { put(it, "i") }; "ঔৌ".forEach { put(it, "u") }
        "ঋৃ".forEach { put(it, "ri") }
        // অ ও ো are "o" (dropped); ঁ ঃ are not pronounced as letters.
        "অওোঁঃ".forEach { put(it, "") }
    }

    fun ofLatin(text: String): String {
        var s = text.lowercase()
        for ((from, to) in latinDigraphs) s = s.replace(from, to)
        val out = StringBuilder()
        for (ch in s) {
            when {
                ch == 'N' -> out.append('N')
                ch.isDigit() -> out.append(ch)
                else -> latinSingles[ch]?.let(out::append)
            }
        }
        return collapse(out)
    }

    fun ofBangla(text: String): String {
        val s = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '্') {
                // য-ফলা sounds like "y" (ব্যাগ = "byag"); ব-ফলা is usually silent (বিশ্বাস).
                when (s.getOrNull(i + 1)) {
                    'য' -> { out.append('y'); i += 2; continue }
                    'ব' -> { i += 2; continue }
                }
                i++; continue
            }
            // য় ড় ঢ় are composition exclusions: in NFC they stay base letter + nukta (U+09BC).
            if (s.getOrNull(i + 1) == NUKTA || ch in PRECOMPOSED) {
                val base = PRECOMPOSED_BASE[ch] ?: ch
                out.append(if (base == 'য') "y" else "r")
                i += if (ch in PRECOMPOSED) 1 else 2
                continue
            }
            when {
                ch in '০'..'৯' -> out.append('0' + (ch - '০'))
                ch.isDigit() -> out.append(ch)
                else -> bangla[ch]?.let(out::append)
            }
            i++
        }
        return collapse(out)
    }

    /** Consonants only: "rahim" and "রহিম" both give "rhm". */
    fun skeleton(key: String): String = key.filter { it !in VOWELS }

    private const val VOWELS = "aiue"
    private const val NUKTA = '\u09BC'

    /** Precomposed য় ড় ঢ় (U+09DF, U+09DC, U+09DD), in case text arrives un-normalised. */
    private val PRECOMPOSED_BASE = mapOf('\u09DF' to 'য', '\u09DC' to 'ড', '\u09DD' to 'ঢ')
    private val PRECOMPOSED = PRECOMPOSED_BASE.keys

    private fun collapse(sb: CharSequence): String {
        val out = StringBuilder(sb.length)
        for (ch in sb) if (out.isEmpty() || out.last() != ch) out.append(ch)
        return out.toString()
    }
}
