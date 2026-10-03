package com.bolohisab.nlu.typing

/**
 * Reads the bundled `vocabulary.txt` (words with English aliases, sentences, next-word hints).
 * Kept as a data file so the vocabulary can grow without code changes; see its header for format.
 */
internal object BuiltInVocabulary {

    class Data(
        val words: List<Pair<String, List<String>>>,
        val phrases: List<String>,
        /** A word (or a [TypingMemory] slot) and what usually follows it, best first. */
        val next: List<Pair<String, List<String>>>,
    )

    val data: Data by lazy {
        val text = BuiltInVocabulary::class.java.getResourceAsStream("vocabulary.txt")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            .orEmpty()
        parse(text)
    }

    fun parse(text: String): Data {
        val words = mutableListOf<Pair<String, List<String>>>()
        val phrases = mutableListOf<String>()
        val next = mutableListOf<Pair<String, List<String>>>()
        var section = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) { section = line; continue }
            val cols = line.split('\t').map(String::trim)
            when (section) {
                "[words]" -> words += cols[0] to cols.getOrNull(1).orEmpty().split(' ').filter(String::isNotEmpty).map(String::lowercase)
                "[phrases]" -> phrases += cols[0]
                "[next]" -> if (cols.size >= 2) next += cols[0] to cols[1].split('|').map(String::trim).filter(String::isNotEmpty)
            }
        }
        return Data(words, phrases, next)
    }
}
