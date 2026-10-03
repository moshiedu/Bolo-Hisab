package com.bolohisab.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The typing-help memory: what this shop's typing and corrections taught the app. */
@Dao
interface LearningDao {

    // ------------------------------------------------------------------ choices

    @Query("UPDATE typing_choices SET count = count + 1, last_used = :now WHERE typed = :typed AND text = :text")
    suspend fun bumpChoice(typed: String, text: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChoice(choice: TypingChoiceEntity)

    @Transaction
    suspend fun recordChoice(typed: String, text: String, now: Long) {
        if (bumpChoice(typed, text, now) == 0) insertChoice(TypingChoiceEntity(typed, text, 1, now))
    }

    @Query("SELECT * FROM typing_choices ORDER BY count DESC, last_used DESC LIMIT :limit")
    fun observeChoices(limit: Int): Flow<List<TypingChoiceEntity>>

    @Query("DELETE FROM typing_choices WHERE typed = :typed AND text = :text")
    suspend fun deleteChoice(typed: String, text: String)

    // -------------------------------------------------------------------- usage

    @Query("UPDATE word_usage SET count = count + 1, last_used = :now WHERE word = :word")
    suspend fun bumpWord(word: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: WordUsageEntity)

    @Query("SELECT * FROM word_usage ORDER BY count DESC, last_used DESC LIMIT :limit")
    fun observeWords(limit: Int): Flow<List<WordUsageEntity>>

    @Query("UPDATE word_pairs SET count = count + 1, last_used = :now WHERE prev = :prev AND next = :next")
    suspend fun bumpPair(prev: String, next: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPair(pair: WordPairEntity)

    @Query("SELECT * FROM word_pairs ORDER BY count DESC, last_used DESC LIMIT :limit")
    fun observePairs(limit: Int): Flow<List<WordPairEntity>>

    // -------------------------------------------------------------- corrections

    @Query("SELECT * FROM corrections WHERE heard = :heard")
    suspend fun correction(heard: String): CorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCorrection(correction: CorrectionEntity)

    @Query("SELECT * FROM corrections ORDER BY last_used DESC")
    fun observeCorrections(): Flow<List<CorrectionEntity>>

    @Query("DELETE FROM corrections WHERE heard = :heard")
    suspend fun deleteCorrection(heard: String)

    // ------------------------------------------------------------------ upkeep

    /** Keeps the tables small on a budget phone: the most used rows survive. */
    @Query("DELETE FROM typing_choices WHERE rowid NOT IN (SELECT rowid FROM typing_choices ORDER BY count DESC, last_used DESC LIMIT :keep)")
    suspend fun pruneChoices(keep: Int)

    @Query("DELETE FROM word_usage WHERE rowid NOT IN (SELECT rowid FROM word_usage ORDER BY count DESC, last_used DESC LIMIT :keep)")
    suspend fun pruneWords(keep: Int)

    @Query("DELETE FROM word_pairs WHERE rowid NOT IN (SELECT rowid FROM word_pairs ORDER BY count DESC, last_used DESC LIMIT :keep)")
    suspend fun prunePairs(keep: Int)

    @Query("DELETE FROM typing_choices")
    suspend fun clearChoices()

    @Query("DELETE FROM word_usage")
    suspend fun clearWords()

    @Query("DELETE FROM word_pairs")
    suspend fun clearPairs()

    @Query("DELETE FROM corrections")
    suspend fun clearCorrections()

    // ------------------------------------------------------------------ backup

    @Query("SELECT * FROM typing_choices")
    suspend fun allChoicesOnce(): List<TypingChoiceEntity>

    @Query("SELECT * FROM word_usage")
    suspend fun allWordsOnce(): List<WordUsageEntity>

    @Query("SELECT * FROM word_pairs")
    suspend fun allPairsOnce(): List<WordPairEntity>

    @Query("SELECT * FROM corrections")
    suspend fun allCorrectionsOnce(): List<CorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChoices(rows: List<TypingChoiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(rows: List<WordUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairs(rows: List<WordPairEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrections(rows: List<CorrectionEntity>)

    @Transaction
    suspend fun replaceAll(
        choices: List<TypingChoiceEntity>,
        words: List<WordUsageEntity>,
        pairs: List<WordPairEntity>,
        corrections: List<CorrectionEntity>,
    ) {
        clearChoices(); clearWords(); clearPairs(); clearCorrections()
        insertChoices(choices); insertWords(words); insertPairs(pairs); insertCorrections(corrections)
    }
}
