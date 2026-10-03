package com.bolohisab.nlu.typing

import com.bolohisab.nlu.LexiconCsv

/**
 * The bundled typing vocabulary, read from the lexicon CSVs the team edits in a spreadsheet
 * (see docs/lexicon.md): `words.csv` (words and the English/Banglish spellings typed for them),
 * `phrases.csv` (whole sentences) and `next_words.csv` (what usually follows a word).
 */
internal object BuiltInVocabulary {

    class Data(
        val words: List<Pair<String, List<String>>>,
        val phrases: List<String>,
        /** A word (or a [TypingMemory] slot) and what usually follows it, best first. */
        val next: List<Pair<String, List<String>>>,
    )

    val data: Data by lazy {
        Data(
            words = LexiconCsv.load("words.csv").map { it["word"] to LexiconCsv.list(it["aliases"]).map(String::lowercase) }
                .filter { it.first.isNotEmpty() },
            phrases = LexiconCsv.load("phrases.csv").map { it["phrase"] }.filter(String::isNotEmpty),
            // Row order is rank order: the first "after" row for a word is its best follower.
            next = LexiconCsv.load("next_words.csv")
                .filter { it["after"].isNotEmpty() && it["next"].isNotEmpty() }
                .groupBy({ it["after"] }, { it["next"] })
                .toList(),
        )
    }
}
