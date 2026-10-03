package com.bolohisab.nlu

/**
 * Words the speech recogniser is biased towards ("hotwords"), most important first: this shop's
 * customers, its products and past item names, then the goods and units from the shared lexicon.
 * Capped, because every hotword makes decoding a little slower on a budget phone.
 */
object Hotwords {

    const val LIMIT = 300

    fun build(
        customers: Collection<String>,
        products: Collection<String> = emptyList(),
        pastItems: Collection<String> = emptyList(),
        limit: Int = LIMIT,
    ): List<String> {
        val out = LinkedHashMap<String, String>()
        fun add(word: String) {
            val clean = word.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty() || clean.none { it in 'ঀ'..'৿' }) return
            out.putIfAbsent(BanglaText.key(clean), clean)
        }
        customers.forEach(::add)
        products.forEach(::add)
        pastItems.forEach(::add)
        QuantityUnit.entries.forEach { add(it.label) }
        Lexicon.goods.forEach(::add)
        return out.values.take(limit)
    }
}
