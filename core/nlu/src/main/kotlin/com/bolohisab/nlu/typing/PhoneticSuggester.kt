package com.bolohisab.nlu.typing

import com.bolohisab.nlu.BanglaText
import com.bolohisab.nlu.TextSimilarity

enum class SuggestionKind {
    /** A dictionary word matching the whole typed word: safe to commit on space. */
    WORD,

    /** A longer word or sentence that starts with what was typed. Only taken on tap. */
    COMPLETION,

    /** Plain Avro transliteration of the typed Latin. */
    TRANSLITERATION,

    /** The Latin exactly as typed, for names or words meant to stay in English. */
    ORIGINAL,
}

data class Suggestion(val text: String, val kind: SuggestionKind, val source: WordSource? = null)

/**
 * Candidates for the word at the cursor, which spans [start] until [end] in the text.
 * [items] are in display order; the first one is what a space or comma commits.
 */
data class Suggestions(val start: Int, val end: Int, val typed: String, val items: List<Suggestion>) {
    val isLatin: Boolean get() = typed.isNotEmpty() && typed.all { PhoneticSuggester.isLatinChar(it) }

    /** What typing a space should turn the Latin word into, or null to leave it as typed. */
    val commitOnSpace: Suggestion?
        get() = if (isLatin) items.firstOrNull()?.takeIf { it.kind == SuggestionKind.WORD || it.kind == SuggestionKind.TRANSLITERATION } else null
}

/** What kind of field is being typed in, to rank the shop's own names appropriately. */
enum class TypingContext { SENTENCE, CUSTOMER, ITEM, TEXT }

/**
 * Avro-like typing help for Bolo Hisab: type Banglish ("rohim 2 kg chal") and get the ledger's
 * Bangla words ("রহিম", "কেজি", "চাল"), whole sentences ("আজ মোট বিক্রি কত") and the shop's own
 * customers and products, ranked above a plain Avro transliteration. Also completes words typed
 * directly in Bangla script.
 */
class PhoneticSuggester(private val dictionary: TypingDictionary) {

    private data class Match(val score: Double, val whole: Boolean)

    private class Scored(val suggestion: Suggestion, val score: Double, val whole: Boolean)

    fun suggest(text: String, cursor: Int, context: TypingContext = TypingContext.SENTENCE, limit: Int = 5): Suggestions? {
        val at = cursor.coerceIn(0, text.length)
        var start = at
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = at
        while (end < text.length && isWordChar(text[end])) end++
        val typed = text.substring(start, end)
        val previous = previousWords(text, start)

        if (typed.isEmpty()) return continuations(text, start, previous, context, limit)
        val latin = typed.all { isLatinChar(it) }
        val bangla = !latin && typed.all { isBanglaChar(it) }
        if (!latin && !bangla) return null

        val predicted = previous.lastOrNull()?.let { p -> dictionary.nextAfter(p).map { BanglaText.key(it.text) }.toSet() }.orEmpty()
        val scored = mutableListOf<Scored>()
        var keepLatin = false
        if (latin) {
            val lower = typed.lowercase()
            val key = PhoneticKey.ofLatin(typed)
            val skeleton = PhoneticKey.skeleton(key)

            // What this shopkeeper picked for this exact spelling before beats every guess.
            val learned = dictionary.choicesFor(lower)
            keepLatin = learned.firstOrNull()?.let { it.text.equals(typed, ignoreCase = true) } == true
            for (c in learned) {
                if (c.text.equals(typed, ignoreCase = true)) continue
                scored += Scored(Suggestion(c.text, SuggestionKind.WORD, WordSource.LEARNED), LEARNED_SCORE + 0.02 * minOf(c.count, 10), true)
            }
            if (lower.length >= 2) {
                for (c in dictionary.choicesStartingWith(lower)) {
                    if (c.text.any { isBanglaChar(it) }) {
                        scored += Scored(Suggestion(c.text, SuggestionKind.COMPLETION, WordSource.LEARNED), 0.8 + 0.01 * minOf(c.count, 10), false)
                    }
                }
            }

            for (w in dictionary.entries) {
                if (w.isPhrase && w.source != WordSource.DOMAIN) {
                    nameMatch(w, context) { i -> latinMatch(lower, key, skeleton, w.wordKeys[i], w.wordSkeletons[i], emptyList(), loose = true) }
                        ?.let { scored += it }
                    continue
                }
                if (w.isPhrase) {
                    if (context != TypingContext.SENTENCE) continue
                    phraseMatch(w, previous) { k -> latinMatch(lower, key, skeleton, w.wordKeys[k], w.wordSkeletons[k], if (k == 0) w.aliases else emptyList(), loose = false) }
                        ?.let { scored += it }
                    continue
                }
                val m = latinMatch(lower, key, skeleton, w.key, w.skeleton, w.aliases, loose = w.source != WordSource.DOMAIN) ?: continue
                scored += Scored(Suggestion(w.text, if (m.whole) SuggestionKind.WORD else SuggestionKind.COMPLETION, w.source), weigh(m.score, w, context, predicted), m.whole)
            }
        } else {
            val norm = BanglaText.key(typed)
            for (w in dictionary.entries) {
                if (w.isPhrase && w.source != WordSource.DOMAIN) {
                    nameMatch(w, context) { i -> banglaPrefix(norm, w.wordNorms[i]) }?.let { scored += it }
                    continue
                }
                if (w.isPhrase) {
                    if (context != TypingContext.SENTENCE) continue
                    phraseMatch(w, previous) { k -> banglaPrefix(norm, w.wordNorms[k]) }?.let { scored += it }
                    continue
                }
                val m = banglaPrefix(norm, w.norm) ?: continue
                scored += Scored(Suggestion(w.text, SuggestionKind.COMPLETION, w.source), weigh(m.score, w, context, predicted), false)
            }
        }

        val ordered = mutableListOf<Suggestion>()
        fun addAll(list: List<Scored>) = list.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.suggestion.text.length })
            .forEach { s -> if (ordered.none { it.text == s.suggestion.text }) ordered += s.suggestion }

        addAll(scored.filter { it.whole && it.score >= WHOLE_MIN })
        if (latin) {
            val raw = AvroPhonetic.transliterate(typed)
            if (ordered.none { it.text == raw }) ordered += Suggestion(raw, SuggestionKind.TRANSLITERATION)
        }
        addAll(scored.filter { !it.whole && it.score >= COMPLETION_MIN })

        val items = if (latin && keepLatin) {
            // Last time this spelling stayed in English (a name, a brand): offer that first, so
            // space keeps it as typed.
            listOf(Suggestion(typed, SuggestionKind.ORIGINAL)) + ordered.take(limit - 1)
        } else if (latin) {
            ordered.take(limit - 1) + Suggestion(typed, SuggestionKind.ORIGINAL)
        } else {
            ordered.take(limit)
        }
        if (items.isEmpty()) return null
        return Suggestions(start, end, typed, items)
    }

    /** After a space: the rest of a sentence whose first words were just typed ("আজ " -> "মোট বিক্রি কত"). */
    private fun continuations(text: String, at: Int, previous: List<String>, context: TypingContext, limit: Int): Suggestions? {
        if (context != TypingContext.SENTENCE || previous.isEmpty()) return null
        if (at > 0 && !text[at - 1].isWhitespace()) return null
        val scored = mutableListOf<Scored>()
        for (w in dictionary.entries) {
            if (!w.isPhrase) continue
            for (k in 1 until w.words.size) {
                if (previous.size < k) break
                if (previous.takeLast(k) != w.wordNorms.take(k)) continue
                val rest = w.words.drop(k).joinToString(" ")
                scored += Scored(Suggestion(rest, SuggestionKind.COMPLETION, w.source), 0.7 + 0.05 * k, false)
            }
        }
        // What usually comes next: built-in hints ("২ " → কেজি, টাকা) and this shop's own habits.
        previous.lastOrNull()?.let { p ->
            for (n in dictionary.nextAfter(p)) scored += Scored(Suggestion(n.text, SuggestionKind.COMPLETION), n.score, false)
        }
        val items = scored.sortedByDescending { it.score }.map { it.suggestion }.distinctBy { it.text }.take(limit)
        return if (items.isEmpty()) null else Suggestions(at, at, "", items)
    }

    /**
     * Matches the typed word against word k of a phrase, where the k words before it are exactly
     * what was already written. Offers the phrase from word k on.
     */
    private inline fun phraseMatch(w: DictionaryWord, previous: List<String>, matchWord: (Int) -> Match?): Scored? {
        var best: Scored? = null
        for (k in 0 until w.words.size - 1) {
            if (k > 0 && (previous.size < k || previous.takeLast(k) != w.wordNorms.take(k))) continue
            val m = matchWord(k) ?: continue
            val score = m.score * 0.95 + 0.03 * k
            if (best == null || score > best.score) {
                best = Scored(Suggestion(w.words.drop(k).joinToString(" "), SuggestionKind.COMPLETION, w.source), score, false)
            }
        }
        return best
    }

    /**
     * A multi-word customer or product name ("আব্দুল করিম", "মিনিকেট চাল"): any of its words
     * brings up the full name, the way a shopkeeper calls someone just "করিম".
     */
    private inline fun nameMatch(w: DictionaryWord, context: TypingContext, matchWord: (Int) -> Match?): Scored? {
        var best: Match? = null
        for (i in w.words.indices) matchWord(i)?.let { if (best == null || it.score > best!!.score) best = it }
        val m = best ?: return null
        // In a name field, a whole name part ("korim") stands for the whole name: commit it on space.
        val whole = m.whole && (
            (context == TypingContext.CUSTOMER && w.source == WordSource.CUSTOMER) ||
                (context == TypingContext.ITEM && w.source != WordSource.CUSTOMER)
            )
        val kind = if (whole) SuggestionKind.WORD else SuggestionKind.COMPLETION
        return Scored(Suggestion(w.text, kind, w.source), weigh(m.score * 0.97, w, context), whole)
    }

    /**
     * [loose] also accepts a consonant-only match ("rahim" for রহিম), right for names, where
     * the vowel is anyone's guess, but too eager for common words ("taka" is not থেকে).
     */
    private fun latinMatch(
        lower: String, key: String, skeleton: String, wordKey: String, wordSkeleton: String, aliases: List<String>,
        loose: Boolean,
    ): Match? {
        var best: Match? = null
        fun offer(m: Match) { if (best == null || m.score > best!!.score) best = m }

        for (a in aliases) {
            if (a == lower) offer(Match(0.97, true))
            else if (lower.length >= 2 && a.startsWith(lower)) offer(Match(0.6 + 0.3 * lower.length / a.length, false))
        }
        if (key.isNotEmpty()) {
            when {
                key == wordKey -> offer(Match(1.0, true))
                loose && skeleton.length >= 2 && key.length >= 3 && skeleton == wordSkeleton -> offer(Match(0.85, true))
            }
            if (wordKey.length > key.length && wordKey.startsWith(key)) {
                offer(Match(0.6 + 0.3 * key.length / wordKey.length, false))
            }
            if (key.length >= 3) {
                val sim = TextSimilarity.similarity(key, wordKey)
                if (sim >= 0.75) offer(Match(sim * 0.9, true))
            }
            if (skeleton.length >= 2 && wordSkeleton.length > skeleton.length && wordSkeleton.startsWith(skeleton)) {
                offer(Match(0.5 + 0.2 * skeleton.length / wordSkeleton.length, false))
            }
        }
        return best
    }

    private fun banglaPrefix(typed: String, word: String): Match? =
        if (word.length > typed.length && word.startsWith(typed)) Match(0.6 + 0.3 * typed.length / word.length, false) else null

    private fun weigh(score: Double, w: DictionaryWord, context: TypingContext, predicted: Set<String> = emptySet()): Double {
        val boost = when (context) {
            TypingContext.CUSTOMER -> if (w.source == WordSource.CUSTOMER) 1.15 else 0.85
            TypingContext.ITEM -> when (w.source) {
                WordSource.PRODUCT, WordSource.ITEM -> 1.12
                WordSource.CUSTOMER -> 0.8
                WordSource.LEARNED, WordSource.DOMAIN -> 1.0
            }
            TypingContext.SENTENCE -> if (w.source == WordSource.DOMAIN) 1.0 else 1.05
            TypingContext.TEXT -> 1.0
        }
        val likelyNext = if (w.norm in predicted) 1.08 else 1.0
        return score * w.weight * boost * dictionary.usageBoost(w.norm) * likelyNext
    }

    /**
     * The words written before the current one, nearest last: Bangla words normalised, numbers as
     * [TypingMemory.NUMBER], anything else as "" (which matches no phrase or hint).
     */
    private fun previousWords(text: String, start: Int): List<String> =
        text.substring(0, start).trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .takeLast(MAX_CONTEXT)
            .map { raw ->
                val w = raw.trim(',', '.', '।', '?', '!')
                when {
                    w.isNotEmpty() && w.all { it.isDigit() || it == '.' } -> TypingMemory.NUMBER
                    w.isNotEmpty() && w.all { isBanglaChar(it) } -> BanglaText.key(w)
                    else -> ""
                }
            }

    companion object {
        private const val WHOLE_MIN = 0.7
        private const val COMPLETION_MIN = 0.55
        private const val MAX_CONTEXT = 3
        private const val LEARNED_SCORE = 1.3

        internal fun isLatinChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == '^'

        /** Bangla letters and signs, not Bangla digits (those are numbers, handled by the parser). */
        internal fun isBanglaChar(c: Char) = c in 'ঀ'..'৿' && c !in '০'..'৯' && c != '৳'

        private fun isWordChar(c: Char) = isLatinChar(c) || isBanglaChar(c) || c == '‌' || c == '‍'

        /**
         * Puts [choice] in place of the word at the cursor, followed by [trailing] unless the text
         * already continues with a space. Returns the new text and where the cursor goes.
         */
        fun apply(text: String, suggestions: Suggestions, choice: Suggestion, trailing: String = " "): Pair<String, Int> {
            val before = text.substring(0, suggestions.start)
            val after = text.substring(suggestions.end)
            val sep = if (trailing.isNotEmpty() && after.startsWith(trailing)) "" else trailing
            val inserted = choice.text + sep
            val cursor = before.length + inserted.length + if (sep.isEmpty() && trailing.isNotEmpty()) trailing.length else 0
            return (before + inserted + after) to cursor
        }
    }
}
