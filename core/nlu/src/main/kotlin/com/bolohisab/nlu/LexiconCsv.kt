package com.bolohisab.nlu

/**
 * Reads the hand-edited lexicon CSVs bundled under `resources/com/bolohisab/nlu/lexicon/`
 * (words, phrases, next words, dialect). They are prepared in Excel or Google Sheets, so this
 * accepts what those export: a UTF-8 BOM, quoted cells containing commas or quotes, CRLF line
 * ends and blank rows. A row whose first cell starts with `#` is a comment.
 */
object LexiconCsv {

    /** One row, by header name; missing cells read as "". */
    class Row(private val cells: Map<String, String>, val line: Int) {
        operator fun get(column: String): String = cells[column].orEmpty()
    }

    /** Loads a bundled lexicon file, e.g. `load("words.csv")`. Empty if it is missing. */
    fun load(fileName: String): List<Row> {
        val text = LexiconCsv::class.java.getResourceAsStream("/com/bolohisab/nlu/lexicon/$fileName")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return emptyList()
        return parse(text)
    }

    fun parse(text: String): List<Row> {
        val records = records(text.removePrefix("﻿"))
        if (records.isEmpty()) return emptyList()
        val header = records.first().second.map { it.trim().lowercase() }
        return records.drop(1).mapNotNull { (line, cells) ->
            val trimmed = cells.map(String::trim)
            if (trimmed.all(String::isEmpty) || trimmed.first().startsWith("#")) return@mapNotNull null
            Row(header.indices.associate { header[it] to trimmed.getOrElse(it) { "" } }, line)
        }
    }

    /** RFC 4180 records with the 1-based line each one starts on. */
    private fun records(text: String): List<Pair<Int, List<String>>> {
        val out = mutableListOf<Pair<Int, List<String>>>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var line = 1
        var startLine = 1
        var i = 0
        fun endCell() { cells += cell.toString(); cell.setLength(0) }
        fun endRecord() { endCell(); out += startLine to cells; cells = mutableListOf(); startLine = line }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                quoted -> { if (c == '\n') line++; cell.append(c) }
                c == ',' -> endCell()
                c == '\r' -> Unit
                c == '\n' -> { line++; endRecord() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || cells.isNotEmpty()) endRecord()
        return out
    }

    /** Splits a multi-value cell such as aliases: "kg|kilo|keji". */
    fun list(cell: String): List<String> = cell.split('|').map(String::trim).filter(String::isNotEmpty)
}
