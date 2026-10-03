package com.bolohisab.nlu

/**
 * Maps regional and colloquial Bangla words to the standard forms [LedgerParser]'s grammar is
 * written in, so "রহিম ৫০০ টেঁয়া বাকি" (Chittagonian) parses like "রহিম ৫০০ টাকা বাকি".
 *
 * It runs on every transcript before parsing, voice or typed. It only rewrites words with one
 * unambiguous ledger meaning; a word that is also a common name or means something else in
 * standard Bangla stays out (e.g. "টিয়া" is a parrot and a girl's name, not just "taka").
 *
 * Grow it from field data: add a row to `dialect.csv` per word heard in shops, with its region.
 */
object Dialect {

    /** [region] is free text from the CSV: colloquial, chattogram, sylhet, noakhali, … */
    data class Variant(val word: String, val standard: String, val region: String)

    /** Loaded from `dialect.csv`, edited in a spreadsheet with the rest of the lexicon. */
    val variants: List<Variant> by lazy {
        LexiconCsv.load("dialect.csv")
            .map { Variant(it["word"], it["standard"], it["region"]) }
            .filter { it.word.isNotEmpty() && it.standard.isNotEmpty() }
    }

    private val byWord: Map<String, String> by lazy {
        variants.associate { BanglaText.key(it.word) to BanglaText.key(it.standard) }
    }

    /** The standard form of one normalised word, or the word itself. */
    fun standardize(word: String): String = byWord[word] ?: word
}
