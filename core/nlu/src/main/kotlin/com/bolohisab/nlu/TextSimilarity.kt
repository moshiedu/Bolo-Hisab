package com.bolohisab.nlu

import kotlin.math.max

/** Normalised edit-distance similarity, shared by [CustomerMatcher] and [ItemMatcher]. */
internal object TextSimilarity {

    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val len = max(a.length, b.length)
        if (len == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / len
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }
}
