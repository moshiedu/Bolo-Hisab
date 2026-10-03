package com.bolohisab.voice

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Keeps hotword mode from ever crashing the app. sherpa-onnx validates its config in native code and
 * aborts the process on a problem, which no try/catch can stop; and because the copied model loads at
 * every launch, one bad config would mean a crash on every start.
 *
 * So: hotword mode is only tried for a real BPE model with a readable vocab, a marker file is written
 * before each attempt and removed after it, and a marker found at the next start means the attempt
 * killed the app — hotword mode is then switched off for good on this phone (the model still loads
 * normally, without biasing).
 */
internal object HotwordGuard {
    private const val ATTEMPT = "asr-hotword-attempt"
    private const val DISABLED = "asr-hotwords-disabled"
    private const val TAG = "HotwordGuard"

    private fun file(context: Context, name: String) = File(context.noBackupFilesDir, name)

    /** True when hotword mode may be tried with [files]. */
    fun canTry(context: Context, files: AsrModelFiles): Boolean {
        val attempt = file(context, ATTEMPT)
        val disabled = file(context, DISABLED)
        if (attempt.exists()) {
            Log.w(TAG, "The last hotword model load never finished; hotwords are now off on this phone")
            attempt.delete()
            disabled.createNewFile()
        }
        if (disabled.exists()) return false
        return isBpeModel(files)
    }

    /** True once hotword mode was turned off because a load died. */
    fun wasDisabled(context: Context): Boolean = file(context, DISABLED).exists()

    fun beginAttempt(context: Context) {
        runCatching { file(context, ATTEMPT).createNewFile() }
    }

    fun attemptSucceeded(context: Context) {
        file(context, ATTEMPT).delete()
    }

    /**
     * A sentencepiece BPE model has word-start pieces ("▁") in tokens.txt and a vocab file of
     * "piece<TAB>score" lines. Anything else must not be loaded with modelingUnit = "bpe".
     */
    private fun isBpeModel(files: AsrModelFiles): Boolean {
        val vocab = files.bpeVocab?.let(::File) ?: return false
        val tokens = File(files.tokens)
        if (!vocab.isFile || vocab.length() == 0L || !tokens.isFile) return false
        return runCatching { isBpe(tokens.readLines(), vocab.readLines()) }.getOrDefault(false)
    }

    /** The BPE check on file contents, shared with the Settings switch's check of the bundled model. */
    fun isBpe(tokenLines: List<String>, vocabLines: List<String>): Boolean {
        val vocabOk = vocabLines.isNotEmpty() && vocabLines.asSequence().take(50).filter { it.isNotBlank() }.all { line ->
            val cols = line.split(TAB)
            cols.size >= 2 && cols[1].trim().toFloatOrNull() != null
        }
        return vocabOk && tokenLines.any { WORD_START in it }
    }

    private const val TAB = '\t'
    private const val WORD_START = '\u2581' // sentencepiece "▁"
}
