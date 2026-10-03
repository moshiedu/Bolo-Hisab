package com.bolohisab.nlu.typing

/**
 * What this shop's own use has taught the typing help. Kept on the phone only (in the encrypted
 * ledger database), never shared.
 */
data class TypingMemory(
    /** Banglish spellings and the Bangla the shopkeeper picked for them. */
    val choices: List<LearnedChoice> = emptyList(),
    /** Words used in saved entries, with how often. */
    val words: List<LearnedWord> = emptyList(),
    /** Word pairs seen in saved entries ("চাল" then "টাকা"), for next-word suggestions. */
    val pairs: List<LearnedPair> = emptyList(),
) {
    companion object {
        val EMPTY = TypingMemory()

        /** Stands for any number in [LearnedPair.prev]: "২ কেজি" is learned as <number> → কেজি. */
        const val NUMBER = "<number>"

        /** Stands for any customer's name in the built-in next-word table. */
        const val CUSTOMER = "<customer>"
    }
}

/** [typed] is lower-case Latin; [text] is what was chosen for it, which may be [typed] itself. */
data class LearnedChoice(val typed: String, val text: String, val count: Int)

data class LearnedWord(val word: String, val count: Int)

data class LearnedPair(val prev: String, val next: String, val count: Int)
