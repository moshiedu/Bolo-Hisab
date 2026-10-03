package com.bolohisab.nlu

/**
 * Maps regional and colloquial Bangla words to the standard forms [LedgerParser]'s grammar is
 * written in, so "রহিম ৫০০ টেঁয়া বাকি" (Chittagonian) parses like "রহিম ৫০০ টাকা বাকি".
 *
 * It runs on every transcript before parsing, voice or typed. It only rewrites words with one
 * unambiguous ledger meaning; a word that is also a common name or means something else in
 * standard Bangla stays out (e.g. "টিয়া" is a parrot and a girl's name, not just "taka").
 *
 * Grow it from field data: add a [Variant] per word heard in shops, tagged with its region.
 */
object Dialect {

    enum class Region { COLLOQUIAL, CHATTOGRAM, SYLHET }

    data class Variant(val word: String, val standard: String, val region: Region)

    val variants: List<Variant> = listOf(
        // Money
        Variant("ট্যাকা", "টাকা", Region.COLLOQUIAL),
        Variant("টেকা", "টাকা", Region.COLLOQUIAL),
        Variant("টেঁয়া", "টাকা", Region.CHATTOGRAM),
        Variant("টেয়া", "টাকা", Region.CHATTOGRAM),
        Variant("টেখা", "টাকা", Region.SYLHET),
        // "How much": "কয় টাকা বাকি?"
        Variant("কয়", "কত", Region.COLLOQUIAL),
    )

    private val byWord: Map<String, String> =
        variants.associate { BanglaText.key(it.word) to BanglaText.key(it.standard) }

    /** The standard form of one normalised word, or the word itself. */
    fun standardize(word: String): String = byWord[word] ?: word
}
