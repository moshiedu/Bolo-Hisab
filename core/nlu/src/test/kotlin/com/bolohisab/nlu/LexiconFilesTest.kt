package com.bolohisab.nlu

import org.junit.Assert.fail
import org.junit.Test

/**
 * Catches editing mistakes in the lexicon CSVs before they reach a phone: missing or non-Bangla
 * words, duplicates, malformed aliases, and dialect rows that would hijack a grammar word.
 * Every problem is reported with its file and line.
 */
class LexiconFilesTest {

    private val problems = mutableListOf<String>()

    private fun bangla(s: String) = s.any { it in 'ঀ'..'৿' }

    private fun check(file: String, row: LexiconCsv.Row, ok: Boolean, message: String) {
        if (!ok) problems += "$file line ${row.line}: $message"
    }

    private fun requireColumns(file: String, vararg columns: String): List<LexiconCsv.Row> {
        val rows = LexiconCsv.load(file)
        if (rows.isEmpty()) problems += "$file: missing or empty"
        val missing = columns.filter { c -> rows.isNotEmpty() && rows.all { it[c].isEmpty() } && c in setOf("word", "phrase", "after", "next", "standard") }
        if (missing.isNotEmpty()) problems += "$file: column(s) ${missing.joinToString()} are empty or misnamed"
        return rows
    }

    private fun report() { if (problems.isNotEmpty()) fail(problems.joinToString("\n")) }

    @Test fun words() {
        val seen = HashMap<String, Int>()
        for (r in requireColumns("words.csv", "word", "aliases", "category")) {
            val w = r["word"]
            check("words.csv", r, bangla(w), "word \"$w\" has no Bangla letters")
            seen.put(BanglaText.key(w), r.line)?.let { check("words.csv", r, false, "\"$w\" repeats line $it") }
            for (a in LexiconCsv.list(r["aliases"])) {
                check("words.csv", r, a.all { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() }, "alias \"$a\" must be English letters only (separate aliases with |)")
            }
            check("words.csv", r, r["category"].isNotEmpty(), "\"$w\" has no category")
        }
        report()
    }

    @Test fun phrases() {
        val seen = HashMap<String, Int>()
        for (r in requireColumns("phrases.csv", "phrase")) {
            val p = r["phrase"]
            check("phrases.csv", r, bangla(p) && p.trim().contains(' '), "\"$p\" should be two or more Bangla words")
            seen.put(BanglaText.key(p), r.line)?.let { check("phrases.csv", r, false, "\"$p\" repeats line $it") }
        }
        report()
    }

    @Test fun nextWords() {
        val slots = setOf("<number>", "<customer>")
        val seen = HashMap<String, Int>()
        for (r in requireColumns("next_words.csv", "after", "next")) {
            val after = r["after"]
            val next = r["next"]
            check("next_words.csv", r, after in slots || (bangla(after) && ' ' !in after.trim()), "after \"$after\" must be one Bangla word, <number> or <customer>")
            check("next_words.csv", r, bangla(next), "next \"$next\" has no Bangla letters")
            seen.put(BanglaText.key(after) + "→" + BanglaText.key(next), r.line)?.let { check("next_words.csv", r, false, "$after → $next repeats line $it") }
        }
        report()
    }

    @Test fun dialect() {
        val seen = HashMap<String, Int>()
        for (r in requireColumns("dialect.csv", "word", "standard", "region")) {
            val w = BanglaText.key(r["word"])
            val std = BanglaText.key(r["standard"])
            check("dialect.csv", r, bangla(w) && ' ' !in w, "word \"${r["word"]}\" must be one Bangla word")
            check("dialect.csv", r, bangla(std), "standard \"${r["standard"]}\" has no Bangla letters")
            check("dialect.csv", r, w != std, "\"${r["word"]}\" maps to itself")
            check("dialect.csv", r, w !in Lexicon.allWords && QuantityUnit.of(w) == null, "\"${r["word"]}\" is already a grammar or unit word; mapping it would change its meaning everywhere")
            check("dialect.csv", r, BanglaNumbers.parse(listOf(w)).none { it is Token.Num }, "\"${r["word"]}\" is a number word")
            check("dialect.csv", r, r["region"].isNotEmpty(), "\"${r["word"]}\" has no region")
            seen.put(w, r.line)?.let { check("dialect.csv", r, false, "\"${r["word"]}\" repeats line $it") }
        }
        report()
    }
}
