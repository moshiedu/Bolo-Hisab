package com.bolohisab.nlu

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Runs every row of `test_sentences.csv` (real shop sentences with the expected result) through
 * the parser and reports all mismatches at once, with their CSV line numbers.
 *
 * Columns: sentence, type, customer, total, paid, items, note. A blank expected cell is not
 * checked; customer "-" means "no customer". Known customers are [customers] below.
 */
class SentenceCorpusTest {

    private val customers = listOf(
        KnownCustomer(1, "রহিম"),
        KnownCustomer(2, "আব্দুল করিম"),
        KnownCustomer(3, "মিলি"),
        KnownCustomer(4, "জাফর"),
    )

    private fun rows(): List<LexiconCsv.Row> {
        val text = javaClass.getResourceAsStream("/com/bolohisab/nlu/test_sentences.csv")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return LexiconCsv.parse(text)
    }

    private fun kind(r: ParseResult): String = when (r) {
        is ParseResult.Unrecognized -> "unrecognized"
        is ParseResult.Query -> when (r.query) {
            is LedgerQuery.CustomerDue -> "due_question"
            is LedgerQuery.Sales -> "sales_question"
            LedgerQuery.TopDebtors -> "top_debtors"
        }
        is ParseResult.Entry -> when (r.draft.type) {
            EntryType.CASH_SALE -> "cash_sale"
            EntryType.CREDIT_SALE -> "credit_sale"
            EntryType.PAYMENT_RECEIVED -> "payment"
            EntryType.EXPENSE -> "expense"
        }
    }

    private fun key(s: String) = BanglaText.key(s)

    @Test fun everySentenceParsesAsExpected() {
        val parser = LedgerParser(customers)
        val rows = rows()
        assertTrue("test_sentences.csv has no rows", rows.isNotEmpty())
        val failures = mutableListOf<String>()

        for (row in rows) {
            val sentence = row["sentence"]
            val result = parser.parse(sentence)
            val problems = mutableListOf<String>()
            val expectType = row["type"]
            if (expectType.isNotEmpty() && kind(result) != expectType) problems += "type ${kind(result)} ≠ $expectType"

            val customer = when (result) {
                is ParseResult.Entry -> result.draft.customer?.name
                is ParseResult.Query -> (result.query as? LedgerQuery.CustomerDue)?.customer?.name
                else -> null
            }
            when (val expect = row["customer"]) {
                "" -> Unit
                "-" -> if (customer != null) problems += "customer $customer ≠ none"
                else -> if (customer == null || key(customer) != key(expect)) problems += "customer $customer ≠ $expect"
            }

            val draft = (result as? ParseResult.Entry)?.draft
            fun money(column: String, actual: Poisha?) {
                val expect = row[column].ifEmpty { return }
                val want = Poisha.ofTaka(expect.toDouble())
                if (actual != want) problems += "$column ${actual?.taka} ≠ $expect"
            }
            money("total", draft?.total)
            money("paid", draft?.paid)
            row["items"].ifEmpty { null }?.let { if (draft?.items?.size != it.toInt()) problems += "items ${draft?.items?.size} ≠ $it" }

            if (problems.isNotEmpty()) failures += "line ${row.line}: \"$sentence\" → ${problems.joinToString("; ")}"
        }
        if (failures.isNotEmpty()) fail("${failures.size} of ${rows.size} sentences failed:\n" + failures.joinToString("\n"))
    }
}
