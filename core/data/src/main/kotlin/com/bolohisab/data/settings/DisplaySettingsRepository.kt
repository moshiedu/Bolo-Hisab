package com.bolohisab.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Bengali (১২৩) or Latin (123) numerals for on-screen amounts, dates and quantities. */
enum class DigitStyle { BENGALI, LATIN }

/** Display language for the app's own UI text — independent of [DigitStyle] and of Bangla voice/text parsing. */
enum class AppLanguage { BANGLA, ENGLISH }

@Singleton
class DisplaySettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val digitStyle: Flow<DigitStyle> = dataStore.data.map { prefs ->
        if (prefs[KEY_DIGIT_STYLE] == LATIN_VALUE) DigitStyle.LATIN else DigitStyle.BENGALI
    }

    suspend fun setDigitStyle(style: DigitStyle) {
        dataStore.edit { prefs -> prefs[KEY_DIGIT_STYLE] = if (style == DigitStyle.LATIN) LATIN_VALUE else BENGALI_VALUE }
    }

    val language: Flow<AppLanguage> = dataStore.data.map { prefs ->
        if (prefs[KEY_APP_LANGUAGE] == ENGLISH_VALUE) AppLanguage.ENGLISH else AppLanguage.BANGLA
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { prefs -> prefs[KEY_APP_LANGUAGE] = if (language == AppLanguage.ENGLISH) ENGLISH_VALUE else BANGLA_VALUE }
    }

    private companion object {
        val KEY_DIGIT_STYLE = stringPreferencesKey("digit_style")
        const val LATIN_VALUE = "latin"
        const val BENGALI_VALUE = "bengali"
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        const val ENGLISH_VALUE = "english"
        const val BANGLA_VALUE = "bangla"
    }
}
