package com.bolohisab.nlu

/** Quantity units a shopkeeper says aloud, with the words that name them. */
enum class QuantityUnit(val label: String, words: List<String>) {
    KG("কেজি", listOf("কেজি", "কিলো", "কেজির", "কিলোগ্রাম", "kg")),
    GRAM("গ্রাম", listOf("গ্রাম", "গ্রামের", "g", "gm")),
    LITRE("লিটার", listOf("লিটার", "লিটারের", "লি", "l")),
    // Not "মিলি" alone: it is also a common name.
    ML("মিলি", listOf("মিলিলিটার", "এমএল", "ml")),
    PIECE("পিস", listOf("পিস", "পিছ", "টা", "টি", "টো", "টে", "খানা", "খান", "pc", "pcs")),
    HALI("হালি", listOf("হালি")),
    DOZEN("ডজন", listOf("ডজন")),
    PACKET("প্যাকেট", listOf("প্যাকেট", "প্যাকেটের", "পাকেট")),
    BOTTLE("বোতল", listOf("বোতল", "বোতলের")),
    SACK("বস্তা", listOf("বস্তা", "বস্তার")),
    BOX("বক্স", listOf("বক্স", "কার্টন", "কার্টুন")),
    POA("পোয়া", listOf("পোয়া")),
    SEER("সের", listOf("সের")),
    ;

    val keys: Set<String> = words.map(BanglaText::key).toSet()

    companion object {
        private val byWord: Map<String, QuantityUnit> =
            entries.flatMap { u -> u.keys.map { it to u } }.toMap()

        fun of(word: String): QuantityUnit? = byWord[word]
    }
}

/** Closed word classes the ledger grammar keys on. All entries are normalised. */
internal object Lexicon {
    private fun set(vararg w: String) = w.map(BanglaText::key).toSet()

    val money = set("টাকা", "টাকার", "টাকায়", "টাকাও", "টাকাই", "টাকাটা", "tk", "taka", "৳")
    val credit = set("বাকি", "বাকী", "বাকিতে", "বাকীতে", "ধার", "ধারে", "উধার", "বাকির", "পাওনা", "পাব", "পাবো")
    val received = set(
        "দিয়েছে", "দিয়েছেন", "দিল", "দিলো", "দিলেন", "দিছে", "দিসে", "দিছেন", "দিসেন",
        "দিয়ে", "জমা", "শোধ", "পরিশোধ", "পেলাম", "পাইলাম", "পেয়েছি", "পাইছি", "আদায়", "আদায়",
    )
    val cash = set("নগদ", "নগদে", "ক্যাশ", "ক্যাশে", "কেশ")
    val sale = set("বিক্রি", "বিক্রয়", "বেচা", "বেচলাম", "বেচছি", "বিক্রি হলো", "সেল")
    val expense = set("খরচ", "ব্যয়", "খরচা")
    val expenseTopics = set("ভাড়া", "বিল", "বেতন", "মজুরি", "বিদ্যুৎ", "কারেন্ট", "গাড়িভাড়া", "ভ্যানভাড়া")
    val total = set("মোট", "সব", "মিলিয়ে", "টোটাল", "সর্বমোট")
    val conjunctions = set("আর", "এবং", "ও", "সাথে", "সঙ্গে", "আরো", "আরও")
    val honorifics = set(
        "ভাই", "ভাইয়া", "ভাবি", "ভাবী", "আপা", "আপু", "দাদা", "দিদি", "চাচা", "চাচি", "মামা", "মামি",
        "কাকা", "কাকি", "খালা", "খালু", "সাহেব", "সাব", "মিয়া", "মিয়া", "বাবু", "ভাবিজান", "বু",
    )
    val question = set("কত", "কতো", "কত?", "কতটা", "কেমন")
    val due = set("পাওনা", "পাব", "পাবো", "পাই", "বাকি", "বাকী")
    val today = set("আজ", "আজকে", "আজকের")
    val yesterday = set("গতকাল", "গতকালকে", "কাল", "কালকে")
    val month = set("মাস", "মাসে", "মাসের")
    val week = set("সপ্তাহ", "সপ্তাহে", "সপ্তাহের")
    val most = set("সবচেয়ে", "সবচাইতে", "বেশি")
    val who = set("কার", "কাদের", "কে")

    /**
     * The generic word for "a customer"/"someone" — never a real name. Includes "কাস্টমা", the
     * form ASR commonly produces by dropping a weak word-final "র" ("কাস্টমার" -> "কাস্টমা"),
     * so a sentence like "কাস্টমা ২ লিটার তেল কিনেছে" doesn't create a customer literally named that.
     */
    val genericCustomer = set("কাস্টমার", "কাস্টমা", "কাষ্টমার", "কাষ্টমা", "কাস্টোমার", "একজন", "কেউ")

    /** Verbs and filler that carry no item or customer meaning. */
    val filler = set(
        "দিলাম", "দিলাম", "দিছি", "দিসি", "দিয়েছি", "নিল", "নিলো", "নিয়েছে", "নিছে", "নিসে", "নিলেন",
        "নিয়ে", "গেছে", "গেল", "গেলো", "গেছেন", "হলো", "হল", "হয়েছে", "আছে", "করে", "করেছে", "করলাম",
        "কাছে", "থেকে", "কে", "একটু", "তো", "আমার", "আমি", "মানে", "হয়", "হইছে", "ছিল", "বাবদ",
        "জন্য", "দাম", "দামে", "করে", "প্রতি", "লাগল", "লাগলো", "হইল", "এখন", "দেওয়া", "দেয়া", "নেওয়া",
        "উনি", "সে", "তিনি", "উনার", "ওর", "তার",
    )

    /** Every grammar word above, for checks that must never touch them (see [Corrections]). */
    val allWords: Set<String> by lazy {
        money + credit + received + cash + sale + expense + expenseTopics + total + conjunctions + honorifics +
            question + due + today + yesterday + month + week + most + who + genericCustomer + filler
    }

    /** Common Bangladeshi grocery words, so they are never mistaken for customer names. */
    val items = set(
        "চাল", "ডাল", "তেল", "চিনি", "লবণ", "লবন", "আটা", "ময়দা", "সুজি", "ডিম", "দুধ", "সাবান",
        "পেঁয়াজ", "পিয়াজ", "রসুন", "আদা", "আলু", "মরিচ", "হলুদ", "জিরা", "চা", "চাপাতা", "কফি",
        "বিস্কুট", "সেমাই", "নুডলস", "শ্যাম্পু", "টুথপেস্ট", "ব্রাশ", "মুড়ি", "চিড়া", "গুড়", "মসুর",
        "মুগ", "ছোলা", "খেসারি", "সয়াবিন", "সরিষা", "কেরোসিন", "ব্যাটারি", "মোমবাতি", "দিয়াশলাই",
        "কলম", "খাতা", "ওষুধ", "পানি", "কোক", "জুস", "রুটি", "পাউরুটি", "কলা", "মাছ", "মাংস",
        "মুরগি", "ডিটারজেন্ট", "হুইল", "লাক্স", "ফ্রুটিকা", "চানাচুর", "চকলেট", "লজেন্স", "সিগারেট",
        "পান", "সুপারি", "ঘি", "মাখন", "ডালডা", "মশলা", "মসলা", "শুঁটকি", "টিস্যু", "স্যাভলন", "ডেটল",
    )
}
