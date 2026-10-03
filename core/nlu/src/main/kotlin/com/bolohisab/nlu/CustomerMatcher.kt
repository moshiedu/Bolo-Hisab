package com.bolohisab.nlu

import kotlin.math.max

/**
 * Finds which known customer a spoken word refers to.
 *
 * Handles Bangla case endings ("রহিমের", "করিমকে", "মিলিরে"), matches a single part
 * of a longer name ("করিম" -> "আব্দুল করিম"), and tolerates small speech-recognition
 * errors through a normalised edit distance.
 */
class CustomerMatcher(customers: List<KnownCustomer>) {

    private data class Entry(val customer: KnownCustomer, val full: String, val parts: List<String>)

    private val entries = customers.map { c ->
        val words = BanglaText.tokenize(c.name).filterNot { it in Lexicon.honorifics }
        Entry(c, words.joinToString(" "), words)
    }

    data class Match(val customer: KnownCustomer, val score: Double)

    /** Best match for [spoken] (one or two words), or null below [threshold]. */
    fun match(spoken: String, threshold: Double = 0.8): Match? {
        if (entries.isEmpty()) return null
        var best: Match? = null
        for (form in forms(spoken)) {
            for (e in entries) {
                val score = score(form, e)
                if (best == null || score > best.score) best = Match(e.customer, score)
            }
        }
        return best?.takeIf { it.score >= threshold }
    }

    private fun score(form: String, e: Entry): Double {
        if (form == e.full) return 1.0
        val parts = e.parts
        if (parts.size > 1 && form in parts) return 0.92
        val whole = TextSimilarity.similarity(form, e.full)
        val part = parts.maxOfOrNull { TextSimilarity.similarity(form, it) }?.let { it * 0.95 } ?: 0.0
        return max(whole, part)
    }

    companion object {
        /** Case endings, longest first. Each is tried, and the unstripped word is kept too. */
        private val suffixes = listOf(
            "দেরকে", "য়েরকে", "েরকে", "দের", "য়ের", "য়েরে", "েরে", "ের", "কেও", "কে", "রে", "র", "য়ে", "এর",
        ).map(BanglaText::key)

        fun forms(word: String): List<String> {
            val w = BanglaText.normalize(word).trim()
            val out = linkedSetOf(w)
            for (s in suffixes) {
                if (w.length > s.length + 1 && w.endsWith(s)) out += w.removeSuffix(s)
            }
            return out.toList()
        }

        private val bareR = setOf(BanglaText.key("র"), BanglaText.key("রে"))

        private fun isVowelSign(c: Char) = c in 'া'..'ৌ' || c == 'ৗ'

        /**
         * The case ending on [w], if any. A bare "র"/"রে" only counts after a vowel sign
         * ("মিলির" -> "মিলি"), so names that end in র ("জাফর") stay whole.
         */
        private fun endingOf(w: String): String? = suffixes.firstOrNull { s ->
            w.length > s.length + 2 && w.endsWith(s) &&
                (s !in bareR || isVowelSign(w[w.length - s.length - 1]))
        }

        /** Strips a case ending from a new (unknown) name: "জামালকে" -> "জামাল". */
        fun baseName(word: String): String {
            val w = BanglaText.normalize(word).trim()
            return endingOf(w)?.let { w.removeSuffix(it) } ?: w
        }

        fun hasCaseEnding(word: String): Boolean = endingOf(BanglaText.normalize(word).trim()) != null
    }
}
