package com.bolohisab.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemMatcherTest {

    private val items = listOf(KnownItem(1, "চাল"), KnownItem(2, "সয়াবিন তেল"), KnownItem(3, "ডাল"))

    @Test
    fun `matches an exact item name`() {
        val match = ItemMatcher(items).match("চাল")
        assertEquals(1L, match?.item?.id)
    }

    @Test
    fun `tolerates a small spelling difference`() {
        val match = ItemMatcher(items).match("সয়াবিন তেল")
        assertEquals(2L, match?.item?.id)
    }

    @Test
    fun `returns null for an unrelated word`() {
        assertNull(ItemMatcher(items).match("সাবান"))
    }

    @Test
    fun `returns null with no tracked items`() {
        assertNull(ItemMatcher(emptyList()).match("চাল"))
    }
}
