package com.bolohisab.nlu

/**
 * Turns one spoken (or typed) Bangla sentence into a ledger entry draft or a question.
 *
 * Deterministic and offline: number normalisation, a keyword grammar and fuzzy
 * customer matching. Every guess it is unsure of is reported in [EntryDraft.uncertain]
 * so the confirm card can ask the shopkeeper to check it.
 *
 * Example: "রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, ১০০ দিয়েছে"
 * -> credit sale to রহিম, 2 items, total ৳320, paid ৳100, due ৳220.
 */
class LedgerParser(
    customers: List<KnownCustomer> = emptyList(),
    knownItems: Collection<String> = emptyList(),
    /** Learned fixes for words speech recognition keeps getting wrong, see [Corrections]. */
    corrections: Map<String, String> = emptyMap(),
) {
    private val fixes: Map<String, String> =
        corrections.entries.associate { (wrong, right) -> BanglaText.key(wrong) to BanglaText.key(right) }

    private val matcher = CustomerMatcher(customers)
    private val itemWords: Set<String> = Lexicon.goods + knownItems.map(BanglaText::key)

    fun parse(transcript: String): ParseResult {
        val words = Corrections.apply(BanglaText.tokenize(transcript).map(Dialect::standardize), fixes)
        val tokens = BanglaNumbers.parse(words)
        if (tokens.none { it !is Token.Sep }) return ParseResult.Unrecognized(transcript)

        detectQuery(tokens, transcript)?.let { return it }

        val (customer, consumed) = detectCustomer(tokens)
        val body = tokens.filterIndexed { i, _ -> i !in consumed }
        return buildEntry(body, customer, transcript)
    }

    // ---------------------------------------------------------------- queries

    private fun detectQuery(tokens: List<Token>, transcript: String): ParseResult.Query? {
        val words = tokens.filterIsInstance<Token.Word>().map { it.text }
        val set = words.toSet()
        if (set.none { it in Lexicon.question } && set.none { it in Lexicon.who }) return null
        val asksWho = set.any { it in Lexicon.who }
        val asksHowMuch = set.any { it in Lexicon.question }

        if (asksWho && set.any { it in Lexicon.most } && set.any { it in Lexicon.due }) {
            return ParseResult.Query(LedgerQuery.TopDebtors, transcript)
        }
        if (!asksHowMuch) return null
        if (set.any { it in Lexicon.sale }) {
            val period = when {
                set.any { it in Lexicon.yesterday } -> Period.YESTERDAY
                set.any { it in Lexicon.week } -> Period.THIS_WEEK
                set.any { it in Lexicon.month } -> Period.THIS_MONTH
                else -> Period.TODAY
            }
            return ParseResult.Query(LedgerQuery.Sales(period), transcript)
        }
        if (set.any { it in Lexicon.due }) {
            val (customer, _) = detectCustomer(tokens)
            return ParseResult.Query(LedgerQuery.CustomerDue(customer), transcript)
        }
        return null
    }

    // --------------------------------------------------------------- customer

    /** "ভাই", and with a case ending: "ভাইয়ের", "আপাকে". */
    private fun isHonorific(w: String?): Boolean =
        w != null && (w in Lexicon.honorifics || CustomerMatcher.forms(w).any { it in Lexicon.honorifics })

    private fun isGrammarWord(w: String): Boolean =
        w in Lexicon.money || w in Lexicon.credit || w in Lexicon.received || w in Lexicon.cash ||
            w in Lexicon.sale || w in Lexicon.expense || w in Lexicon.total || w in Lexicon.conjunctions ||
            isHonorific(w) || w in Lexicon.filler || w in Lexicon.question || w in Lexicon.due ||
            w in Lexicon.today || w in Lexicon.yesterday || w in Lexicon.month || w in Lexicon.week ||
            w in Lexicon.most || w in Lexicon.who || w in Lexicon.genericCustomer || QuantityUnit.of(w) != null

    private fun isItemWord(w: String): Boolean =
        w in itemWords || CustomerMatcher.baseName(w) in itemWords || stripDeterminer(w) in itemWords

    private fun nameCandidate(t: Token?): String? =
        (t as? Token.Word)?.text?.takeIf { !isGrammarWord(it) && !isItemWord(it) && !it.first().isDigit() }

    /** Returns the customer and the token indices it (and a following honorific) used up. */
    private fun detectCustomer(tokens: List<Token>): Pair<CustomerRef?, Set<Int>> {
        var best: Triple<CustomerMatcher.Match, Int, Int>? = null // match, start, endExclusive
        for (i in tokens.indices) {
            val first = nameCandidate(tokens[i]) ?: continue
            matcher.match(first)?.let { m ->
                if (best == null || m.score > best!!.first.score) best = Triple(m, i, i + 1)
            }
            nameCandidate(tokens.getOrNull(i + 1))?.let { second ->
                matcher.match("$first $second")?.let { m ->
                    if (best == null || m.score > best!!.first.score) best = Triple(m, i, i + 2)
                }
            }
        }
        best?.let { (m, start, end) ->
            val used = (start until end).toMutableSet()
            if (isHonorific((tokens.getOrNull(end) as? Token.Word)?.text)) used += end
            return CustomerRef.Existing(m.customer.id, m.customer.name, m.score) to used
        }

        // Unknown customer: a name before an honorific, a name with a case ending,
        // or the first word of the sentence when numbers or goods follow it.
        for (i in tokens.indices) {
            val w = nameCandidate(tokens[i]) ?: continue
            val next = tokens.getOrNull(i + 1)
            val nextWord = (next as? Token.Word)?.text
            when {
                isHonorific(nextWord) ->
                    return CustomerRef.New(CustomerMatcher.baseName(w)) to setOf(i, i + 1)
                CustomerMatcher.hasCaseEnding(w) ->
                    return CustomerRef.New(CustomerMatcher.baseName(w)) to setOf(i)
                i == 0 && (next is Token.Num || (nextWord != null && isItemWord(nextWord))) ->
                    return CustomerRef.New(w) to setOf(i)
            }
        }
        return null to emptySet()
    }

    // ------------------------------------------------------------------ entry

    private enum class Role { QUANTITY, MONEY, PAID, CREDIT, TOTAL, BARE }

    private data class Number(val value: Double, val role: Role, val unit: QuantityUnit?)

    private data class Clause(val words: List<String>, val numbers: List<Number>)

    private fun wordAt(list: List<Token>, i: Int) = (list.getOrNull(i) as? Token.Word)?.text

    private fun splitClauses(body: List<Token>): List<List<Token>> {
        val clauses = mutableListOf<List<Token>>()
        var current = mutableListOf<Token>()
        for (t in body) {
            val isBreak = t is Token.Sep || (t is Token.Word && t.text in Lexicon.conjunctions)
            if (isBreak) {
                if (current.isNotEmpty()) clauses += current
                current = mutableListOf()
            } else {
                current += t
            }
        }
        if (current.isNotEmpty()) clauses += current
        return clauses
    }

    private fun analyse(clause: List<Token>): Clause {
        val numbers = mutableListOf<Number>()
        val words = mutableListOf<String>()
        clause.forEachIndexed { k, t ->
            when (t) {
                is Token.Word -> words += t.text
                is Token.Sep -> Unit
                is Token.Num -> {
                    val next = wordAt(clause, k + 1)
                    val afterNext = wordAt(clause, k + 2)
                    val prev = wordAt(clause, k - 1)
                    val unit = next?.let(QuantityUnit::of)
                    val role = when {
                        unit != null -> Role.QUANTITY
                        prev in Lexicon.total -> Role.TOTAL
                        prev in Lexicon.cash || prev in Lexicon.received -> Role.PAID
                        next in Lexicon.received -> Role.PAID
                        next in Lexicon.money && afterNext in Lexicon.received -> Role.PAID
                        prev in Lexicon.credit -> Role.CREDIT
                        next in Lexicon.money && afterNext in Lexicon.credit -> Role.CREDIT
                        next in Lexicon.credit -> Role.CREDIT
                        next in Lexicon.money -> Role.MONEY
                        else -> Role.BARE
                    }
                    numbers += Number(t.value, role, unit)
                }
            }
        }
        val content = words.filterNot { isGrammarWord(it) }.map(::cleanItemWord)
        return Clause(content, numbers)
    }

    private fun stripDeterminer(w: String): String {
        for (d in determiners) if (w.length > d.length + 1 && w.endsWith(d)) return w.removeSuffix(d)
        return w
    }

    /** "চালটা" -> "চাল", "ডালের" -> "ডাল"; unknown words are kept whole ("পরোটা" stays). */
    private fun cleanItemWord(w: String): String {
        val d = stripDeterminer(w)
        if (d != w && d in itemWords) return d
        val base = CustomerMatcher.baseName(w)
        return if (base in itemWords) base else w
    }

    private fun buildEntry(body: List<Token>, customer: CustomerRef?, transcript: String): ParseResult {
        val allWords = body.filterIsInstance<Token.Word>().map { it.text }.toSet()
        val isCredit = allWords.any { it in Lexicon.credit }
        val isReceived = allWords.any { it in Lexicon.received }
        val paysCash = allWords.any { it in Lexicon.cash }
        val isSale = allWords.any { it in Lexicon.sale }
        val isExpense = allWords.any { it in Lexicon.expense }
        val expenseTopic = allWords.filter { it in Lexicon.expenseTopics }

        val items = mutableListOf<ItemLine>()
        val loose = mutableListOf<Double>()
        val noteWords = mutableListOf<String>()
        var paid: Double? = null
        var credit: Double? = null
        var explicitTotal: Double? = null
        var pendingQty: Number? = null
        val uncertain = mutableSetOf<Field>()

        for (clause in splitClauses(body).map(::analyse)) {
            for (n in clause.numbers) when (n.role) {
                Role.PAID -> paid = (paid ?: 0.0) + n.value
                Role.TOTAL -> explicitTotal = n.value
                else -> Unit
            }
            val creditNumbers = clause.numbers.filter { it.role == Role.CREDIT }.toMutableList()
            val qty = clause.numbers.firstOrNull { it.role == Role.QUANTITY } ?: pendingQty
            val money = clause.numbers.filter { it.role == Role.MONEY }.map { it.value }.toMutableList()
            val bare = clause.numbers.filter { it.role == Role.BARE }.map { it.value }.toMutableList()

            val isExpenseClause = clause.words.any { it in Lexicon.expenseTopics } ||
                (isExpense && customer == null && items.isEmpty())
            val isItemClause = clause.words.isNotEmpty() && !isExpenseClause &&
                (clause.numbers.isNotEmpty() || clause.words.any { it in itemWords })
            if (isItemClause) {
                var quantity = qty?.value
                var unit = qty?.unit
                // "১৩৫ টাকা বাকিতে" inside an item clause is that item's price, given on credit.
                val price = money.removeFirstOrNull() ?: bare.removeLastOrNull()
                    ?: creditNumbers.removeFirstOrNull()?.value
                if (quantity == null && bare.isNotEmpty()) {
                    quantity = bare.removeFirst()
                    unit = null
                }
                items += ItemLine(clause.words.joinToString(" "), quantity, unit, price?.let(Poisha::ofTaka))
                pendingQty = null
                loose += money + bare
            } else {
                if (clause.words.isNotEmpty()) noteWords += clause.words
                if (qty != null && clause.numbers.size == 1) { pendingQty = qty; continue }
                val amounts = money + bare
                val last = items.lastOrNull()
                if (last != null && last.price == null && amounts.isNotEmpty()) {
                    items[items.lastIndex] = last.copy(price = Poisha.ofTaka(amounts.first()))
                    loose += amounts.drop(1)
                } else {
                    loose += amounts
                }
            }
            creditNumbers.lastOrNull()?.let { credit = it.value }
        }

        val note = noteWords.distinct().joinToString(" ").ifBlank { null }

        // ---------------------------------------------------------- goods sold
        if (items.isNotEmpty()) {
            // One unpriced item and a spoken total: the item takes what is left of the total.
            val unpriced = items.indices.filter { items[it].price == null }
            if (unpriced.size == 1 && explicitTotal != null) {
                val rest = Poisha.ofTaka(explicitTotal!!).value - items.sumOf { it.price?.value ?: 0L }
                if (rest > 0) items[unpriced.single()] = items[unpriced.single()].copy(price = Poisha(rest))
            }
            if (items.any { it.price == null }) uncertain += Field.ITEMS
            val itemSum = items.sumOf { it.price?.value ?: 0L }
            val total = when {
                explicitTotal != null -> {
                    if (itemSum > 0 && Poisha.ofTaka(explicitTotal!!).value != itemSum) uncertain += Field.TOTAL
                    Poisha.ofTaka(explicitTotal!!)
                }
                itemSum > 0 -> Poisha(itemSum)
                loose.isNotEmpty() -> Poisha.ofTaka(loose.sum())
                else -> { uncertain += Field.TOTAL; Poisha.ZERO }
            }
            val paidAmount = when {
                paid != null -> Poisha.ofTaka(paid!!)
                credit != null -> {
                    val c = Poisha.ofTaka(credit!!)
                    if (c > total) { uncertain += Field.PAID; Poisha.ZERO } else total - c
                }
                paysCash -> total
                isCredit -> Poisha.ZERO
                customer == null -> total
                else -> { uncertain += Field.TYPE; Poisha.ZERO }
            }
            if (paid != null && credit != null && Poisha.ofTaka(credit!!) != total - paidAmount) {
                uncertain += Field.PAID
            }
            val type = if (paidAmount >= total) EntryType.CASH_SALE else EntryType.CREDIT_SALE
            if (type == EntryType.CREDIT_SALE && customer == null) uncertain += Field.CUSTOMER
            customer.flagIfUnsure(uncertain)
            return entry(type, customer, items, total, minOf(paidAmount, total), note, uncertain, transcript)
        }

        // ------------------------------------------------------------ expense
        if ((isExpense || expenseTopic.isNotEmpty()) && customer == null) {
            val amount = (loose.firstOrNull() ?: paid ?: credit)
                ?: return ParseResult.Unrecognized(transcript)
            val p = Poisha.ofTaka(amount)
            return entry(EntryType.EXPENSE, null, emptyList(), p, p, note, uncertain, transcript)
        }

        // ------------------------------------------------- money-only entries
        val type: EntryType
        val amount: Double
        when {
            paid != null -> { type = EntryType.PAYMENT_RECEIVED; amount = paid!! }
            isCredit && (credit ?: loose.firstOrNull()) != null -> {
                type = EntryType.CREDIT_SALE; amount = credit ?: loose.first()
            }
            isReceived && loose.isNotEmpty() -> { type = EntryType.PAYMENT_RECEIVED; amount = loose.first() }
            (isSale || paysCash) && loose.isNotEmpty() -> { type = EntryType.CASH_SALE; amount = loose.first() }
            loose.isNotEmpty() && customer != null -> {
                type = EntryType.CREDIT_SALE; amount = loose.first(); uncertain += Field.TYPE
            }
            loose.isNotEmpty() -> {
                type = EntryType.CASH_SALE; amount = loose.first(); uncertain += Field.TYPE
            }
            else -> return ParseResult.Unrecognized(transcript)
        }
        if (loose.size > 1) uncertain += Field.AMOUNT
        if (customer == null && type != EntryType.CASH_SALE) uncertain += Field.CUSTOMER
        customer.flagIfUnsure(uncertain)
        val total = Poisha.ofTaka(amount)
        val paidAmount = if (type == EntryType.CREDIT_SALE) Poisha.ZERO else total
        return entry(type, customer, emptyList(), total, paidAmount, note, uncertain, transcript)
    }

    private fun CustomerRef?.flagIfUnsure(uncertain: MutableSet<Field>) {
        when (this) {
            is CustomerRef.New -> uncertain += Field.CUSTOMER
            is CustomerRef.Existing -> if (score < 0.9) uncertain += Field.CUSTOMER
            null -> Unit
        }
    }

    private fun entry(
        type: EntryType, customer: CustomerRef?, items: List<ItemLine>, total: Poisha, paid: Poisha,
        note: String?, uncertain: Set<Field>, transcript: String,
    ) = ParseResult.Entry(EntryDraft(type, customer, items, total, paid, note, uncertain, transcript))

    private companion object {
        val determiners = listOf("গুলো", "গুলা", "টুকু", "টা", "টি").map(BanglaText::key)
    }
}
