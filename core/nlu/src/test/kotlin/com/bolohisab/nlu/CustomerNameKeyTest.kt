package com.bolohisab.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CustomerNameKeyTest {

    @Test fun spacingAndCaseDoNotMakeANewName() {
        assertEquals(CustomerMatcher.nameKey("আব্দুল করিম"), CustomerMatcher.nameKey("  আব্দুল   করিম "))
        assertEquals(CustomerMatcher.nameKey("Rahim"), CustomerMatcher.nameKey("rahim"))
    }

    @Test fun differentSpellingsStayDifferentNames() {
        // Near-misses are for the "did you mean" chip to ask about, never merged silently.
        assertNotEquals(CustomerMatcher.nameKey("রহিম"), CustomerMatcher.nameKey("রহীম"))
    }
}
