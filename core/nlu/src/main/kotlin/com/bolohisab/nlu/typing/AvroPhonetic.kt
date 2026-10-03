package com.bolohisab.nlu.typing

/**
 * Avro-style phonetic transliteration: Latin keystrokes to Bangla script ("amar" -> "আমার").
 *
 * Follows the Avro Phonetic conventions shopkeepers already know from their PC/phone keyboards:
 * case picks the retroflex/alternate letter (t ত, T ট, d দ, D ড, n ন, N ণ, sh শ, Sh ষ, R ড়),
 * a vowel after a consonant becomes its sign (ka -> কা), "o" after a consonant is the inherent
 * vowel (kolom -> কলম), and two consonants with no vowel between them join with a hasanta
 * (kr -> ক্র, rk -> র্ক). "y"/"w" after a consonant are the য/ব phala (bya -> ব্যা).
 *
 * This is the raw fallback; [PhoneticSuggester] ranks dictionary words above it, so loose
 * spellings like "dokan" still reach "দোকান" even though Avro itself gives "দকান".
 */
object AvroPhonetic {

    private sealed interface Rule
    private data class Consonant(val text: String) : Rule
    private data class Vowel(val independent: String, val sign: String) : Rule

    /** Marks that never take a hasanta before them and end a consonant cluster. */
    private data class Mark(val text: String) : Rule

    /** য-ফলা / ব-ফলা after a consonant, the given letter elsewhere. */
    private data class Phala(val phala: String, val alone: Rule) : Rule

    private val rules: Map<String, Rule> = buildMap {
        fun c(k: String, v: String) = put(k, Consonant(v))
        fun v(k: String, independent: String, sign: String) = put(k, Vowel(independent, sign))

        c("k", "ক"); c("kh", "খ"); c("g", "গ"); c("gh", "ঘ"); c("Ng", "ঙ")
        c("c", "চ"); c("ch", "ছ"); c("j", "জ"); c("jh", "ঝ"); c("NG", "ঞ"); c("J", "জ")
        c("T", "ট"); c("Th", "ঠ"); c("D", "ড"); c("Dh", "ঢ"); c("N", "ণ")
        c("t", "ত"); c("th", "থ"); c("d", "দ"); c("dh", "ধ"); c("n", "ন")
        c("p", "প"); c("ph", "ফ"); c("f", "ফ"); c("b", "ব"); c("bh", "ভ"); c("v", "ভ"); c("m", "ম")
        c("z", "য"); c("r", "র"); c("l", "ল"); c("sh", "শ"); c("S", "শ"); c("Sh", "ষ"); c("s", "স"); c("h", "হ")
        c("R", "ড়"); c("Rh", "ঢ়"); c("Y", "য়"); c("q", "ক"); c("x", "ক্স"); c("kSh", "ক্ষ")

        v("o", "অ", ""); v("a", "আ", "া"); v("A", "আ", "া")
        v("i", "ই", "ি"); v("I", "ঈ", "ী"); v("ee", "ঈ", "ী")
        v("u", "উ", "ু"); v("U", "ঊ", "ূ"); v("oo", "উ", "ু")
        v("e", "এ", "ে"); v("E", "এ", "ে"); v("O", "ও", "ো"); v("OI", "ঐ", "ৈ"); v("OU", "ঔ", "ৌ")
        v("rri", "ঋ", "ৃ")

        put("y", Phala("্য", Consonant("য়")))
        put("w", Phala("্ব", Vowel("ও", "ো")))
        put("ng", Mark("ং")); put("^", Mark("ঁ")); put("$", Mark("৳"))
    }

    private val maxKey = rules.keys.maxOf { it.length }

    /** Transliterates [input]; characters with no rule (digits, spaces, punctuation) pass through. */
    fun transliterate(input: String): String {
        val src = unAutoCapitalise(input)
        val out = StringBuilder(src.length + 4)
        var afterConsonant = false
        var i = 0
        while (i < src.length) {
            var matched: Rule? = null
            var len = minOf(maxKey, src.length - i)
            while (len > 0) {
                matched = rules[src.substring(i, i + len)]
                if (matched != null) break
                len--
            }
            when (val rule = matched) {
                null -> { out.append(src[i]); afterConsonant = false; i++; continue }
                is Consonant -> {
                    if (afterConsonant) out.append('্')
                    out.append(rule.text)
                    afterConsonant = true
                }
                is Vowel -> {
                    out.append(if (afterConsonant) rule.sign else rule.independent)
                    afterConsonant = false
                }
                is Mark -> { out.append(rule.text); afterConsonant = false }
                is Phala -> if (afterConsonant) {
                    // Stays "after a consonant" so the next vowel is a sign: bya -> ব্যা.
                    out.append(rule.phala)
                } else {
                    when (val alone = rule.alone) {
                        is Consonant -> { out.append(alone.text); afterConsonant = true }
                        is Vowel -> { out.append(alone.independent); afterConsonant = false }
                        else -> Unit
                    }
                }
            }
            i += len
        }
        return out.toString()
    }

    /**
     * Phone keyboards capitalise the first letter of a sentence ("Chal"), which in Avro would
     * mean a different letter. Undo that when only the first letter is upper-case; deliberate
     * Avro capitals mid-word (koT, baRi) are kept.
     */
    private fun unAutoCapitalise(word: String): String {
        if (word.isEmpty() || !word[0].isUpperCase()) return word
        val rest = word.drop(1)
        return if (rest.none { it.isUpperCase() }) word[0].lowercaseChar() + rest else word
    }
}
