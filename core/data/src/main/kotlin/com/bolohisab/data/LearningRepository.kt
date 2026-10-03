package com.bolohisab.data

import androidx.room.withTransaction
import com.bolohisab.data.db.CorrectionEntity
import com.bolohisab.data.db.LearningDao
import com.bolohisab.data.db.LedgerDatabase
import com.bolohisab.data.db.WordPairEntity
import com.bolohisab.data.db.WordUsageEntity
import com.bolohisab.nlu.Corrections
import com.bolohisab.nlu.EntryDraft
import com.bolohisab.nlu.KnownCustomer
import com.bolohisab.nlu.typing.LearnedChoice
import com.bolohisab.nlu.typing.LearnedPair
import com.bolohisab.nlu.typing.LearnedWord
import com.bolohisab.nlu.typing.TypingMemory
import com.bolohisab.nlu.typing.UsageLearner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** A learned fix, as shown in Settings. */
data class LearnedCorrection(val heard: String, val fixed: String, val count: Int)

/**
 * What the app learns from this shop: Banglish → Bangla picks, the words and word pairs used in
 * saved entries, and fixes for words speech recognition gets wrong. Everything stays in the
 * encrypted ledger database on the phone and can be reviewed and deleted in Settings.
 */
@Singleton
class LearningRepository @Inject constructor(
    private val db: LedgerDatabase,
    private val dao: LearningDao,
    private val clock: Clock,
) {
    val memory: Flow<TypingMemory> = combine(
        dao.observeChoices(KEEP_CHOICES),
        dao.observeWords(KEEP_WORDS),
        dao.observePairs(KEEP_PAIRS),
    ) { choices, words, pairs ->
        TypingMemory(
            choices = choices.map { LearnedChoice(it.typed, it.text, it.count) },
            words = words.map { LearnedWord(it.word, it.count) },
            pairs = pairs.map { LearnedPair(it.prev, it.next, it.count) },
        )
    }

    /** Learned fixes as heard → fixed, for [com.bolohisab.nlu.LedgerParser]. */
    val corrections: Flow<Map<String, String>> =
        dao.observeCorrections().map { list -> list.associate { it.heard to it.fixed } }

    val correctionList: Flow<List<LearnedCorrection>> =
        dao.observeCorrections().map { list -> list.map { LearnedCorrection(it.heard, it.fixed, it.count) } }

    val choiceList: Flow<List<LearnedChoice>> =
        dao.observeChoices(KEEP_CHOICES).map { list -> list.map { LearnedChoice(it.typed, it.text, it.count) } }

    /** The shopkeeper picked [text] for the Banglish [typed] (or kept [typed] as it is). */
    suspend fun recordChoice(typed: String, text: String) {
        val key = typed.trim().lowercase()
        if (key.isEmpty() || text.isBlank()) return
        dao.recordChoice(key, text.trim(), clock.millis())
    }

    /**
     * Learns from one entry the shopkeeper confirmed: corrections between what was [parsed] and
     * what was [saved], and the words and pairs used. [parsed] is null for entries typed straight
     * into the card, which teach usage only.
     */
    suspend fun learnFromEntry(
        parsed: EntryDraft?,
        saved: EntryDraft,
        customers: List<KnownCustomer>,
        products: Collection<String>,
    ) = db.withTransaction {
        val now = clock.millis()
        if (parsed != null) {
            for ((heard, fixed) in Corrections.learn(parsed, saved, customers, products)) {
                val existing = dao.correction(heard)
                val count = if (existing?.fixed == fixed) existing.count + 1 else 1
                dao.putCorrection(CorrectionEntity(heard, fixed, count, now))
            }
        }
        val fixes = dao.allCorrectionsOnce().associate { it.heard to it.fixed }
        val usage = UsageLearner.from(saved, fixes)
        for (word in usage.words) {
            if (dao.bumpWord(word, now) == 0) dao.insertWord(WordUsageEntity(word, 1, now))
        }
        for ((prev, next) in usage.pairs) {
            if (dao.bumpPair(prev, next, now) == 0) dao.insertPair(WordPairEntity(prev, next, 1, now))
        }
        dao.pruneWords(KEEP_WORDS)
        dao.prunePairs(KEEP_PAIRS)
        dao.pruneChoices(KEEP_CHOICES)
    }

    suspend fun forgetCorrection(heard: String) = dao.deleteCorrection(heard)

    suspend fun forgetChoice(typed: String, text: String) = dao.deleteChoice(typed, text)

    /** Wipes everything learned; the built-in vocabulary is unaffected. */
    suspend fun forgetAll() = db.withTransaction {
        dao.clearChoices(); dao.clearWords(); dao.clearPairs(); dao.clearCorrections()
    }

    private companion object {
        const val KEEP_CHOICES = 2_000
        const val KEEP_WORDS = 3_000
        const val KEEP_PAIRS = 5_000
    }
}
