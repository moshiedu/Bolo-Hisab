package com.bolohisab.nlu

/**
 * Inverse text normalisation for spoken Bangla numbers.
 *
 * Bangla has a distinct word for every number from 0 to 99, then multipliers
 * (শো = 100, হাজার = 1,000, লাখ = 1,00,000, কোটি = 1,00,00,000), fractions
 * (দেড় = 1.5, আড়াই = 2.5) and modifiers (সাড়ে +½, সোয়া +¼, পৌনে −¼).
 *
 * "সাড়ে তিনশো" -> 350, "এক হাজার দুইশো পঞ্চাশ" -> 1250, "দেড়শো" -> 150,
 * "দুইটা" -> 2 followed by the classifier token "টা".
 */
object BanglaNumbers {

    private val units: Map<String, Double> = buildMap {
        val words = listOf(
            "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়",
            "দশ", "এগারো", "বারো", "তেরো", "চৌদ্দ", "পনেরো", "ষোলো", "সতেরো", "আঠারো", "উনিশ",
            "বিশ", "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আঠাশ", "উনত্রিশ",
            "ত্রিশ", "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "উনচল্লিশ",
            "চল্লিশ", "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "উনপঞ্চাশ",
            "পঞ্চাশ", "একান্ন", "বাহান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "উনষাট",
            "ষাট", "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "উনসত্তর",
            "সত্তর", "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "উনআশি",
            "আশি", "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "আটাশি", "উননব্বই",
            "নব্বই", "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই",
        )
        words.forEachIndexed { i, w -> put(BanglaText.key(w), i.toDouble()) }

        // Common colloquial and spelling variants.
        // "বার" (12) is left out on purpose: it also means "times" ("আরেক বার").
        val variants = mapOf(
            "দু" to 2, "ছয়" to 6,
            "এগার" to 11, "তের" to 13, "চোদ্দ" to 14, "পনের" to 15, "ষোল" to 16,
            "সতের" to 17, "আঠার" to 18, "কুড়ি" to 20, "বেয়াল্লিশ" to 42, "উনষাইট" to 59,
        )
        variants.forEach { (w, v) -> putIfAbsent(BanglaText.key(w), v.toDouble()) }

        put(BanglaText.key("দেড়"), 1.5)
        put(BanglaText.key("আড়াই"), 2.5)
        put(BanglaText.key("আধা"), 0.5)
        put(BanglaText.key("আধ"), 0.5)
    }

    private val hundreds = setOf("শো", "শ", "শত", "শতো").map(BanglaText::key).toSet()

    /** Short forms that only appear glued to শো: "ছশো" = 600, "নশো" = 900. */
    private val hundredPrefixes: Map<String, Double> =
        mapOf("ছ" to 6.0, "ন" to 9.0).mapKeys { BanglaText.key(it.key) }

    private val bigMultipliers: Map<String, Double> = mapOf(
        "হাজার" to 1_000.0,
        "লাখ" to 100_000.0,
        "লক্ষ" to 100_000.0,
        "কোটি" to 10_000_000.0,
    ).mapKeys { BanglaText.key(it.key) }

    private val modifiers: Map<String, Double> = mapOf(
        "সাড়ে" to 0.5,
        "সোয়া" to 0.25,
        "পৌনে" to -0.25,
    ).mapKeys { BanglaText.key(it.key) }

    /** Classifiers that turn a number into a count of pieces: একটা, দুটো, তিনটি, চারখানা. */
    val classifiers: List<String> = listOf("খানা", "খান", "টা", "টি", "টো", "টে").map(BanglaText::key)

    private val numeric = Regex("""^\d+(\.\d+)?$""")

    private sealed interface Atom {
        data class Unit(val value: Double) : Atom
        data object Hundred : Atom
        data class Big(val multiplier: Double) : Atom
        data class Modifier(val delta: Double) : Atom
    }

    /** Converts a word into number atoms, or null if it is not a number word. */
    private fun atomsOf(word: String): List<Atom>? {
        if (numeric.matches(word)) return listOf(Atom.Unit(word.toDouble()))
        units[word]?.let { return listOf(Atom.Unit(it)) }
        if (word in hundreds) return listOf(Atom.Hundred)
        bigMultipliers[word]?.let { return listOf(Atom.Big(it)) }
        modifiers[word]?.let { return listOf(Atom.Modifier(it)) }
        // Compounds such as "তিনশো", "দেড়শো", "পাঁচশত".
        for (suffix in hundreds.sortedByDescending { it.length }) {
            if (word.length > suffix.length && word.endsWith(suffix)) {
                val prefix = word.removeSuffix(suffix)
                (units[prefix] ?: hundredPrefixes[prefix])?.let {
                    return listOf(Atom.Unit(it), Atom.Hundred)
                }
            }
        }
        return null
    }

    /** Splits "দুইটা" into ("দুই", "টা"); returns null when the word has no classifier. */
    private fun splitClassifier(word: String): Pair<String, String>? {
        for (c in classifiers) {
            if (word.length > c.length && word.endsWith(c)) {
                val base = word.removeSuffix(c)
                if (atomsOf(base) != null) return base to c
            }
        }
        return null
    }

    /**
     * Replaces number phrases in a token stream with [Token.Num].
     * Input tokens come from [BanglaText.tokenize].
     */
    fun parse(words: List<String>): List<Token> {
        val out = ArrayList<Token>(words.size)
        var total = 0.0
        var current = 0.0
        var modifier = 0.0
        var active = false
        var lastWasUnit = false
        val raw = StringBuilder()

        fun flush() {
            if (active) out += Token.Num(total + current, raw.toString().trim())
            total = 0.0; current = 0.0; modifier = 0.0
            active = false; lastWasUnit = false; raw.clear()
        }

        for (w in words) {
            if (w == BanglaText.SEPARATOR) { flush(); out += Token.Sep; continue }

            var word = w
            var classifier: String? = null
            if (atomsOf(word) == null) {
                splitClassifier(word)?.let { (base, c) -> word = base; classifier = c }
            }
            val atoms = atomsOf(word)
            if (atoms == null) { flush(); out += Token.Word(w); continue }

            for (atom in atoms) {
                when (atom) {
                    is Atom.Unit -> {
                        if (lastWasUnit) flush()
                        current += atom.value + modifier
                        modifier = 0.0
                        active = true
                        lastWasUnit = true
                    }
                    Atom.Hundred -> {
                        if (current == 0.0) current = 1.0 + modifier.also { modifier = 0.0 }
                        current *= 100
                        active = true
                        lastWasUnit = false
                    }
                    is Atom.Big -> {
                        if (current == 0.0) current = 1.0
                        total += current * atom.multiplier
                        current = 0.0
                        active = true
                        lastWasUnit = false
                    }
                    is Atom.Modifier -> {
                        if (active && lastWasUnit) flush()
                        modifier = atom.delta
                    }
                }
            }
            raw.append(w).append(' ')
            classifier?.let { flush(); out += Token.Word(it) }
        }
        flush()
        return out
    }

    /** Convenience: the first number found in [text], or null. */
    fun firstNumber(text: String): Double? =
        parse(BanglaText.tokenize(text)).firstNotNullOfOrNull { (it as? Token.Num)?.value }
}

sealed interface Token {
    data class Word(val text: String) : Token
    data class Num(val value: Double, val raw: String) : Token
    data object Sep : Token
}
