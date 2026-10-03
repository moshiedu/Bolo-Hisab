package com.bolohisab.nlu.typing

import com.bolohisab.nlu.BanglaText
import com.bolohisab.nlu.Lexicon
import com.bolohisab.nlu.QuantityUnit

/** Where a dictionary word came from; the suggester ranks the shop's own names highest. */
enum class WordSource { CUSTOMER, PRODUCT, ITEM, DOMAIN }

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

/**
 * Bolo Hisab's typing vocabulary: what a shopkeeper actually writes in a ledger (goods, units,
 * money and credit verbs, questions, number words, honorifics), common full sentences, and the
 * shop's own customers and products. Built once per change of customers/products.
 */
class TypingDictionary private constructor(val entries: List<DictionaryWord>) {

    class Builder {
        private val byText = LinkedHashMap<String, DictionaryWord>()

        fun add(text: String, source: WordSource, weight: Double = 1.0, aliases: List<String> = emptyList()): Builder {
            val clean = text.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty() || clean.none { it in 'ঀ'..'৿' }) return this
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

        fun customers(names: Collection<String>) = apply { names.forEach { add(it, WordSource.CUSTOMER, 1.0) } }
        fun products(names: Collection<String>) = apply { names.forEach { add(it, WordSource.PRODUCT, 1.0) } }

        /** Item names from past entries: the shop's real vocabulary, including local brand names. */
        fun pastItems(names: Collection<String>) = apply { names.forEach { add(it, WordSource.ITEM, 0.95) } }

        fun build() = TypingDictionary(byText.values.toList())

        private fun rank(source: WordSource, weight: Double) = (WordSource.entries.size - source.ordinal) * 10 + weight
    }

    companion object {
        /** The built-in Bolo Hisab vocabulary plus the shop's own names. */
        fun forShop(
            customers: Collection<String> = emptyList(),
            products: Collection<String> = emptyList(),
            pastItems: Collection<String> = emptyList(),
        ): TypingDictionary = builtIn().customers(customers).products(products).pastItems(pastItems).build()

        fun builtIn(): Builder = Builder().apply {
            core.forEach { (word, aliases) -> add(word, WordSource.DOMAIN, 1.0, aliases) }
            phrases.forEach { add(it, WordSource.DOMAIN, 1.0) }
            numbers.forEach { add(it, WordSource.DOMAIN, 0.95) }
            QuantityUnit.entries.forEach { u -> add(u.label, WordSource.DOMAIN, 1.0) }
            listOf(
                Lexicon.items, Lexicon.money, Lexicon.credit, Lexicon.received, Lexicon.cash, Lexicon.sale,
                Lexicon.expense, Lexicon.expenseTopics, Lexicon.total, Lexicon.conjunctions, Lexicon.honorifics,
                Lexicon.question, Lexicon.today, Lexicon.yesterday, Lexicon.month, Lexicon.week, Lexicon.most,
                Lexicon.who, Lexicon.filler,
            ).forEach { set -> set.forEach { add(it, WordSource.DOMAIN, 0.9) } }
        }

        /** The most-used ledger words, with the English words shopkeepers type for them. */
        private val core: List<Pair<String, List<String>>> = listOf(
            "টাকা" to listOf("tk", "taka"),
            "বাকি" to listOf("due", "credit", "baki"),
            "জমা" to listOf("deposit", "joma"),
            "দিয়েছে" to listOf("paid", "dise", "dice", "diyeche"),
            "দিলাম" to listOf("dilam"),
            "নিয়েছে" to listOf("nise", "niyeche"),
            "পেলাম" to listOf("received", "pelam"),
            "শোধ" to listOf("shodh"),
            "নগদ" to listOf("cash", "nogod"),
            "বিক্রি" to listOf("sale", "sold", "sell", "bikri"),
            "খরচ" to listOf("expense", "cost", "khoroch"),
            "মোট" to listOf("total", "mot"),
            "কত" to listOf("koto"),
            "আজ" to listOf("today", "aj", "aaj"),
            "গতকাল" to listOf("yesterday", "gotokal"),
            "সপ্তাহ" to listOf("week", "soptah"),
            "মাস" to listOf("month", "mas"),
            "ফেরত" to listOf("return", "ferot"),
            "দোকান" to listOf("shop", "dokan"),
            "ভাড়া" to listOf("rent", "vara", "bhara"),
            "বিল" to listOf("bill"),
            "বেতন" to listOf("salary", "beton"),
            "বিদ্যুৎ" to listOf("electricity", "biddut"),
            "হিসাব" to listOf("hisab", "account"),
            "কেজি" to listOf("kg", "kilo", "keji"),
            "গ্রাম" to listOf("gm", "gram", "g"),
            "লিটার" to listOf("liter", "litre", "ltr"),
            "পিস" to listOf("pc", "pcs", "piece", "pis"),
            "ডজন" to listOf("dozen"),
            "হালি" to listOf("hali"),
            "প্যাকেট" to listOf("packet", "pkt"),
            "বোতল" to listOf("bottle"),
            "বস্তা" to listOf("sack", "bosta"),
            "চাল" to listOf("rice"),
            "ডাল" to listOf("lentil", "dal"),
            "তেল" to listOf("oil"),
            "সয়াবিন" to listOf("soybean"),
            "চিনি" to listOf("sugar"),
            "লবণ" to listOf("salt", "lobon"),
            "আটা" to listOf("flour", "atta"),
            "ময়দা" to listOf("moida", "maida"),
            "ডিম" to listOf("egg", "eggs"),
            "দুধ" to listOf("milk", "dudh"),
            "সাবান" to listOf("soap"),
            "পেঁয়াজ" to listOf("onion", "peyaj", "piyaj"),
            "রসুন" to listOf("garlic"),
            "আদা" to listOf("ginger"),
            "আলু" to listOf("potato", "alu"),
            "মরিচ" to listOf("chili", "chilli", "morich"),
            "হলুদ" to listOf("turmeric", "holud"),
            "চা" to listOf("tea", "cha"),
            "বিস্কুট" to listOf("biscuit", "biscuits"),
            "নুডলস" to listOf("noodles"),
            "শ্যাম্পু" to listOf("shampoo"),
            "টুথপেস্ট" to listOf("toothpaste"),
            "ব্যাটারি" to listOf("battery"),
            "মোমবাতি" to listOf("candle"),
            "কলম" to listOf("pen"),
            "খাতা" to listOf("khata", "notebook"),
            "পানি" to listOf("water", "pani"),
            "পাউরুটি" to listOf("bread"),
            "কলা" to listOf("banana"),
            "মাছ" to listOf("fish"),
            "মাংস" to listOf("meat"),
            "মুরগি" to listOf("chicken", "murgi"),
            "ডিটারজেন্ট" to listOf("detergent"),
            "চকলেট" to listOf("chocolate"),
            "সিগারেট" to listOf("cigarette"),
            "ওষুধ" to listOf("medicine", "oshudh"),
            "আর" to listOf("and", "ar"),
            "ভাই" to listOf("bhai", "vai"),
            "আপা" to listOf("apa"),
            "কার" to listOf("kar"),
            "সবচেয়ে" to listOf("sobcheye"),
            "বেশি" to listOf("beshi"),
        )

        /** Whole sentences and fixed pairs; typing their first word offers the rest. */
        private val phrases = listOf(
            "আজ মোট বিক্রি কত",
            "গতকাল মোট বিক্রি কত",
            "এই সপ্তাহে মোট বিক্রি কত",
            "এই মাসে মোট বিক্রি কত",
            "সবচেয়ে বেশি বাকি কার",
            "কত টাকা বাকি",
            "টাকা বাকি",
            "টাকা দিয়েছে",
            "টাকা জমা দিয়েছে",
            "নগদ দিয়েছে",
            "বাকিতে নিয়েছে",
            "দোকান ভাড়া",
            "বিদ্যুৎ বিল",
            "কর্মচারীর বেতন",
            "গাড়ি ভাড়া",
        )

        private val numbers = listOf(
            "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়", "দশ", "বিশ", "পঁচিশ", "ত্রিশ",
            "চল্লিশ", "পঞ্চাশ", "ষাট", "সত্তর", "আশি", "নব্বই", "একশো", "দেড়শো", "দুইশো", "আড়াইশো",
            "তিনশো", "চারশো", "পাঁচশো", "হাজার", "লাখ", "দেড়", "আড়াই", "সাড়ে", "আধা", "একটা", "দুইটা",
        )
    }
}
