package com.bolohisab.ui.format

import android.content.Context
import android.content.res.Configuration
import com.bolohisab.data.settings.AppLanguage
import java.util.Locale

/**
 * Forces the app's display language via a wrapped [Configuration], independent of the platform's
 * per-app language API (`AppCompatDelegate.setApplicationLocales`/`LocaleManager`), which proved
 * unreliable on some OEM Android builds — the call succeeded silently but never actually applied.
 *
 * The choice is cached in a small dedicated [android.content.SharedPreferences] file (separate from
 * the DataStore-backed [com.bolohisab.data.settings.DisplaySettingsRepository], which remains the
 * reactive source of truth for the rest of the app) purely so [android.app.Activity.attachBaseContext]
 * can read it synchronously before Hilt/DataStore are available that early in the lifecycle.
 */
object LocaleBootstrap {
    private const val PREFS = "locale_bootstrap"
    private const val KEY_LANGUAGE = "language"
    private const val ENGLISH_VALUE = "english"

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, if (language == AppLanguage.ENGLISH) ENGLISH_VALUE else "bangla")
            .apply()
    }

    fun read(context: Context): AppLanguage {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return if (value == ENGLISH_VALUE) AppLanguage.ENGLISH else AppLanguage.BANGLA
    }

    fun wrap(context: Context, language: AppLanguage): Context {
        val locale = Locale.Builder().setLanguage(if (language == AppLanguage.ENGLISH) "en" else "bn").build()
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
