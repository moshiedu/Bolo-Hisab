package com.bolohisab.nlu

/**
 * Finds which tracked product a sale's spoken/typed item name refers to, so stock can be
 * adjusted automatically. Item names are already cleaned by [LedgerParser] before reaching
 * here, so this only needs a normalised similarity check — no case-ending stripping.
 */
class ItemMatcher(items: List<KnownItem>) {

    private data class Entry(val item: KnownItem, val key: String)

    private val entries = items.map { Entry(it, BanglaText.key(it.name)) }

    data class Match(val item: KnownItem, val score: Double)

    /** Best match for [spoken], or null below [threshold]. Deliberately strict: a wrong stock match is worse than none. */
    fun match(spoken: String, threshold: Double = 0.85): Match? {
        if (entries.isEmpty()) return null
        val form = BanglaText.key(spoken)
        if (form.isEmpty()) return null
        var best: Match? = null
        for (e in entries) {
            val score = TextSimilarity.similarity(form, e.key)
            if (best == null || score > best.score) best = Match(e.item, score)
        }
        return best?.takeIf { it.score >= threshold }
    }
}
