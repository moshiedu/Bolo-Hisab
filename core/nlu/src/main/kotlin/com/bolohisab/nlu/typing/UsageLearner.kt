package com.bolohisab.nlu.typing

import com.bolohisab.nlu.BanglaText
import com.bolohisab.nlu.Corrections
import com.bolohisab.nlu.Dialect
import com.bolohisab.nlu.EntryDraft

/**
 * What one saved entry teaches the typing help: the Bangla words the shopkeeper actually uses,
 * and which word follows which ("চাল" → "টাকা", a number → "কেজি"), so the next sentence is
 * predicted the way this shop talks. Learned only from entries confirmed on the card.
 */
object UsageLearner {

    data class Usage(val words: List<String>, val pairs: List<Pair<String, String>>)

    fun from(saved: EntryDraft, fixes: Map<String, String> = emptyMap()): Usage {
        val words = LinkedHashSet<String>()
        val pairs = LinkedHashSet<Pair<String, String>>()

        val tokens = Corrections.apply(BanglaText.tokenize(saved.transcript).map(Dialect::standardize), fixes)
        var prev: String? = null
        for (t in tokens) {
            val slot = when {
                t == BanglaText.SEPARATOR -> null
                t.all { it.isDigit() || it == '.' } -> TypingMemory.NUMBER
                t.length >= 2 && t.all { PhoneticSuggester.isBanglaChar(it) } -> t
                else -> null
            }
            if (slot == null) { prev = null; continue }
            if (slot != TypingMemory.NUMBER) {
                words += slot
                prev?.let { pairs += it to slot }
            }
            prev = slot
        }

        // Fields typed on the card (manual entries have no transcript).
        val fields = listOfNotNull(saved.customer?.name, saved.note) + saved.items.map { it.name }
        for (field in fields) {
            BanglaText.tokenize(field)
                .filter { w -> w.length >= 2 && w.all { PhoneticSuggester.isBanglaChar(it) } }
                .forEach { words += it }
        }
        return Usage(words.toList(), pairs.toList())
    }
}
