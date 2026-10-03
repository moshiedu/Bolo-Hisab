package com.bolohisab.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bolohisab.data.settings.AppLanguage
import java.util.Locale

/**
 * Reads short answers aloud with the phone's text-to-speech, in Bangla (or English when the app is
 * in English). Phones without a Bangla voice simply stay silent; the answer is always on screen.
 */
class Speaker(context: Context, private val language: AppLanguage) {
    private var ready = false
    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS && pickVoice()
        if (ready) pending?.let { say(it) }
        pending = null
    }

    private fun pickVoice(): Boolean {
        val candidates = if (language == AppLanguage.ENGLISH) {
            listOf(Locale.ENGLISH)
        } else {
            listOf(Locale("bn", "BD"), Locale("bn", "IN"), Locale("bn"))
        }
        return candidates.any { locale ->
            val result = tts.setLanguage(locale)
            result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String) {
        val spoken = forSpeech(text)
        if (ready) say(spoken) else pending = spoken
    }

    fun stop() = runCatching { tts.stop() }

    fun shutdown() = runCatching { tts.shutdown() }

    private fun say(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bolohisab-answer")
    }

    /** "৳১২০" reads badly as a symbol; say "১২০ টাকা" (or "120 taka") instead. */
    private fun forSpeech(text: String): String {
        val word = if (language == AppLanguage.ENGLISH) "taka" else "টাকা"
        return text.replace(Regex("৳\\s?([0-9০-৯][0-9০-৯,.]*)"), "$1 $word")
    }
}

/** A [Speaker] tied to the composable's lifetime. */
@Composable
fun rememberSpeaker(language: AppLanguage): Speaker {
    val context = LocalContext.current
    val speaker = remember(language) { Speaker(context, language) }
    DisposableEffect(speaker) { onDispose { speaker.stop(); speaker.shutdown() } }
    return speaker
}
