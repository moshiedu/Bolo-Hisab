package com.bolohisab.nlu

import org.junit.Assert.assertEquals
import org.junit.Test

class LexiconCsvTest {

    @Test fun readsWhatSpreadsheetsExport() {
        val text = "﻿word,aliases,note\r\n" +
            "টাকা,tk|taka,\r\n" +
            "\r\n" +
            "# a comment row,,\r\n" +
            "\"চা পাতা\",tea,\"leaves, loose \"\"premium\"\"\"\r\n"
        val rows = LexiconCsv.parse(text)
        assertEquals(2, rows.size)
        assertEquals("টাকা", rows[0]["word"])
        assertEquals(listOf("tk", "taka"), LexiconCsv.list(rows[0]["aliases"]))
        assertEquals("চা পাতা", rows[1]["word"])
        assertEquals("leaves, loose \"premium\"", rows[1]["note"])
        assertEquals(5, rows[1].line)
        assertEquals("", rows[1]["missing"])
    }
}
