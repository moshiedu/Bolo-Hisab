package com.bolohisab.nlu

import com.bolohisab.nlu.typing.PhoneticKey

/**
 * Word fixes learned from the shopkeeper's own corrections on the confirm card. When speech
 * recognition keeps hearing "রোহিম" and the shopkeeper keeps fixing it to "রহিম", the fix is
 * applied before parsing, so the next "রোহিমের ৫০০ বাকি" lands on the right customer.
 *
 * Learning is deliberately narrow, because a bad fix would silently corrupt every later entry:
 * only a single word that was actually heard, only towards something that sounds alike, and
 * never a word the grammar relies on (money words, units, numbers) or an existing customer's
 * name — changing "রহিম" to "করিম" on the card means a different person, not a mishearing.
 */
object Corrections {

    /** Words that keep their meaning no matter what: grammar, units, numbers, dialect words. */
    fun isProtected(word: String): Boolean {
        val w = BanglaText.key(word)
        if (w.isEmpty() || w.any { it.isDigit() }) return true
        if (w in Lexicon.allWords || QuantityUnit.of(w) != null) return true
        if (Dialect.variants.any { BanglaText.key(it.word) == w || BanglaText.key(it.standard) == w }) return true
        return BanglaNumbers.parse(listOf(w)).any { it is Token.Num }
    }

    /**
     * Rewrites normalised [words] with [fixes] (normalised wrong → right). A case ending on the
     * heard word is kept: with রোহিম → রহিম, "রোহিমের" becomes "রহিমের". A multi-word fix
     * ("করিম" → "আব্দুল করিম") becomes several words.
     */
    fun apply(words: List<String>, fixes: Map<String, String>): List<String> {
        if (fixes.isEmpty()) return words
        return words.flatMap { w ->
            fixes[w]?.let { return@flatMap it.split(' ') }
            for (form in CustomerMatcher.forms(w)) {
                if (form == w) continue
                val right = fixes[form] ?: continue
                return@flatMap (right + w.substring(form.length)).split(' ')
            }
            listOf(w)
        }
    }

    /**
     * Fixes to learn from one saved entry: [parsed] is what the parser made of the transcript,
     * [saved] is what the shopkeeper confirmed. Returns normalised (wrong, right) pairs.
     */
    fun learn(
        parsed: EntryDraft,
        saved: EntryDraft,
        customers: List<KnownCustomer>,
        products: Collection<String>,
    ): List<Pair<String, String>> {
        if (parsed.transcript.isBlank()) return emptyList()
        val heard = BanglaText.tokenize(parsed.transcript).map(Dialect::standardize).toSet()
        val customerKeys = customers.map { CustomerMatcher.nameKey(it.name) }.toSet()
        val productKeys = products.map(BanglaText::key).toSet()

        fun wasHeard(word: String) = word in heard || heard.any { word in CustomerMatcher.forms(it) }
        fun soundsAlike(a: String, b: String) =
            TextSimilarity.similarity(PhoneticKey.ofBangla(a), PhoneticKey.ofBangla(b.replace(" ", ""))) >= SOUND_ALIKE

        fun candidate(wrongRaw: String, rightRaw: String): Pair<String, String>? {
            val wrong = BanglaText.key(wrongRaw)
            val right = CustomerMatcher.nameKey(rightRaw)
            if (wrong.isEmpty() || right.isEmpty() || wrong == right || ' ' in wrong) return null
            if (!wasHeard(wrong) || isProtected(wrong) || wrong in customerKeys || wrong in productKeys) return null
            if (!soundsAlike(wrong, right)) return null
            return wrong to right
        }

        val out = mutableListOf<Pair<String, String>>()
        val heardCustomer = parsed.customer
        val savedCustomer = saved.customer
        if (heardCustomer is CustomerRef.New && savedCustomer != null) {
            candidate(heardCustomer.name, savedCustomer.name)?.let { out += it }
        }
        if (parsed.items.size == saved.items.size) {
            parsed.items.zip(saved.items).forEach { (p, s) -> candidate(p.name, s.name)?.let { out += it } }
        }
        return out.distinctBy { it.first }
    }

    private const val SOUND_ALIKE = 0.6
}
