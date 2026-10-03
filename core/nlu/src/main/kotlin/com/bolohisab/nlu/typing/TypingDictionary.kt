package com.bolohisab.nlu.typing

import com.bolohisab.nlu.BanglaText
import com.bolohisab.nlu.Lexicon
import com.bolohisab.nlu.QuantityUnit
import kotlin.math.ln

/** Where a dictionary word came from; the suggester ranks the shop's own names highest. */
enum class WordSource { CUSTOMER, PRODUCT, ITEM, LEARNED, DOMAIN }

/** One suggestible word or phrase, with the precomputed keys [PhoneticSuggester] matches on. */
class DictionaryWord internal constructor(
    val text: String,
    val source: WordSource,
    val weight: Double,
    /** English/Banglish spellings typed for it: "oil" for তেল, "kg" for কেজি. */
    val aliases: List<String>,
) {
    internal val words: List<String> = text.split(' ').filter { it.isNotEmpty() }
    internal val key: String = PhoneticKey.ofBangla(text.replace(" ", ""))
    internal val skeleton: String = PhoneticKey.skeleton(key)
    internal val norm: String = BanglaText.key(text)
    internal val wordNorms: List<String> = words.map(BanglaText::key)
    internal val wordKeys: List<String> = words.map(PhoneticKey::ofBangla)
    internal val wordSkeletons: List<String> = wordKeys.map(PhoneticKey::skeleton)
    internal val isPhrase: Boolean get() = words.size > 1
}

/** A word likely to come next, and how sure we are. */
internal class NextWord(val text: String, val score: Double)

/**
 * Bolo Hisab's typing vocabulary: what a shopkeeper actually writes in a ledger (goods, units,
 * money and credit verbs, questions, number words, honorifics), common full sentences, and the
 * shop's own customers and products — plus what this shop's typing has taught it
 * ([TypingMemory]). Built once per change of any of those.
 */
class TypingDictionary private constructor(
    val entries: List<DictionaryWord>,
    private val usage: Map<String, Int>,
    private val choices: Map<String, List<LearnedChoice>>,
    private val next: Map<String, List<NextWord>>,
    private val customerWords: Set<String>,
) {
    /** Up to +25% for words this shop uses a lot. */
    internal fun usageBoost(norm: String): Double {
        val n = usage[norm] ?: return 1.0
        return 1.0 + minOf(0.25, 0.06 * ln(1.0 + n))
    }

    /** Past picks for exactly this lower-case Latin spelling, most used first. */
    internal fun choicesFor(typed: String): List<LearnedChoice> = choices[typed].orEmpty()

    /** Past picks for longer spellings that start with [prefix]. */
    internal fun choicesStartingWith(prefix: String): List<LearnedChoice> =
        choices.entries.filter { (k, _) -> k.length > prefix.length && k.startsWith(prefix) }.flatMap { it.value }

    /** What usually follows [previous] (a normalised word or [TypingMemory.NUMBER]), best first. */
    internal fun nextAfter(previous: String): List<NextWord> {
        val direct = next[previous].orEmpty()
        val slot = if (previous in customerWords) next[TypingMemory.CUSTOMER].orEmpty() else emptyList()
        return (direct + slot).groupBy { it.text }.map { (_, v) -> v.maxBy { it.score } }.sortedByDescending { it.score }
    }

    class Builder {
        private val byText = LinkedHashMap<String, DictionaryWord>()
        private val usage = HashMap<String, Int>()
        private val choices = HashMap<String, MutableList<LearnedChoice>>()
        private val next = HashMap<String, MutableMap<String, Double>>()
        private val customerWords = HashSet<String>()

        fun add(text: String, source: WordSource, weight: Double = 1.0, aliases: List<String> = emptyList()): Builder {
            val clean = text.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty() || clean.none { it in '\u0980'..'\u09FF' }) return this
            val norm = BanglaText.key(clean)
            val existing = byText[norm]
            // Keep the strongest source/weight, and merge aliases from every place a word appears.
            if (existing == null || rank(source, weight) > rank(existing.source, existing.weight)) {
                byText[norm] = DictionaryWord(clean, source, weight, (aliases + (existing?.aliases ?: emptyList())).distinct())
            } else if (aliases.isNotEmpty()) {
                byText[norm] = DictionaryWord(existing.text, existing.source, existing.weight, (existing.aliases + aliases).distinct())
            }
            return this
        }

        fun customers(names: Collection<String>) = apply {
            names.forEach { name ->
                add(name, WordSource.CUSTOMER, 1.0)
                name.split(' ').filter { it.isNotBlank() }.forEach { customerWords += BanglaText.key(it) }
            }
        }

        fun products(names: Collection<String>) = apply { names.forEach { add(it, WordSource.PRODUCT, 1.0) } }

        /** Item names from past entries: the shop's real vocabulary, including local brand names. */
        fun pastItems(names: Collection<String>) = apply { names.forEach { add(it, WordSource.ITEM, 0.95) } }

        /** [prev] is a normalised word or a [TypingMemory] slot. */
        fun nextWord(prev: String, text: String, score: Double) = apply {
            val key = if (prev.startsWith("<")) prev else BanglaText.key(prev)
            val bucket = next.getOrPut(key) { LinkedHashMap() }
            bucket[text] = maxOf(bucket[text] ?: 0.0, score)
        }

        fun memory(memory: TypingMemory) = apply {
            for (w in memory.words) {
                val norm = BanglaText.key(w.word)
                usage[norm] = (usage[norm] ?: 0) + w.count
                // A word this shop writes that no list knows yet (a local brand, a nickname).
                if (norm !in byText && w.count >= LEARN_WORD_AFTER) add(w.word, WordSource.LEARNED, 0.95)
            }
            for (c in memory.choices) choices.getOrPut(c.typed.lowercase()) { mutableListOf() } += c
            for (p in memory.pairs) nextWord(p.prev, p.next, 0.8 + minOf(0.15, 0.04 * ln(1.0 + p.count)))
        }

        fun build() = TypingDictionary(
            entries = byText.values.toList(),
            usage = usage.toMap(),
            choices = choices.mapValues { (_, v) -> v.sortedByDescending { it.count } },
            next = next.mapValues { (_, m) -> m.map { (t, s) -> NextWord(t, s) }.sortedByDescending { it.score } },
            customerWords = customerWords.toSet(),
        )

        private fun rank(source: WordSource, weight: Double) = (WordSource.entries.size - source.ordinal) * 10 + weight
    }

    companion object {
        /** A word typed this many times in saved entries joins the dictionary. */
        private const val LEARN_WORD_AFTER = 2

        /** The built-in Bolo Hisab vocabulary, the shop's own names, and what its typing taught us. */
        fun forShop(
            customers: Collection<String> = emptyList(),
            products: Collection<String> = emptyList(),
            pastItems: Collection<String> = emptyList(),
            memory: TypingMemory = TypingMemory.EMPTY,
        ): TypingDictionary =
            builtIn().customers(customers).products(products).pastItems(pastItems).memory(memory).build()

        fun builtIn(): Builder = Builder().apply {
            val vocabulary = BuiltInVocabulary.data
            vocabulary.words.forEach { (word, aliases) -> add(word, WordSource.DOMAIN, 1.0, aliases) }
            vocabulary.phrases.forEach { add(it, WordSource.DOMAIN, 1.0) }
            vocabulary.next.forEach { (prev, list) ->
                list.forEachIndexed { rank, text -> nextWord(prev, text, 0.74 - 0.02 * rank) }
            }
            QuantityUnit.entries.forEach { u -> add(u.label, WordSource.DOMAIN, 1.0) }
            listOf(
                Lexicon.items, Lexicon.money, Lexicon.credit, Lexicon.received, Lexicon.cash, Lexicon.sale,
                Lexicon.expense, Lexicon.expenseTopics, Lexicon.total, Lexicon.conjunctions, Lexicon.honorifics,
                Lexicon.question, Lexicon.today, Lexicon.yesterday, Lexicon.month, Lexicon.week, Lexicon.most,
                Lexicon.who, Lexicon.filler,
            ).forEach { set -> set.forEach { add(it, WordSource.DOMAIN, 0.9) } }
        }
    }
}
