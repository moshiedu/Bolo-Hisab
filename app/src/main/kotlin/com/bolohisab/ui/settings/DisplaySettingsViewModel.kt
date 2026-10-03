package com.bolohisab.ui.settings

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.settings.AppLanguage
import com.bolohisab.data.settings.DigitStyle
import com.bolohisab.data.settings.DisplaySettingsRepository
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.format.LocaleBootstrap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisplaySettingsViewModel @Inject constructor(
    private val repository: DisplaySettingsRepository,
) : ViewModel() {

    val digitStyle: StateFlow<DigitStyle> =
        repository.digitStyle.stateIn(viewModelScope, SharingStarted.Eagerly, DigitStyle.BENGALI)

    fun setDigitStyle(style: DigitStyle) {
        viewModelScope.launch { repository.setDigitStyle(style) }
    }

    val language: StateFlow<AppLanguage> =
        repository.language.stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.BANGLA)

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    val banglishTyping: StateFlow<Boolean> =
        repository.banglishTyping.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setBanglishTyping(enabled: Boolean) {
        viewModelScope.launch { repository.setBanglishTyping(enabled) }
    }
}

/** Keeps [Bn.digitStyle]/[Bn.language] and the app's forced display locale in sync with the persisted choices. */
@Composable
fun DisplaySettingsSync(viewModel: DisplaySettingsViewModel = hiltViewModel()) {
    val style by viewModel.digitStyle.collectAsStateWithLifecycle()
    LaunchedEffect(style) { Bn.digitStyle = style }

    val lang by viewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(lang) {
        Bn.language = lang
        val previous = LocaleBootstrap.read(context)
        LocaleBootstrap.save(context, lang)
        // Cold start: attachBaseContext already applied the persisted value, nothing to do.
        // A real toggle: recreate so attachBaseContext re-wraps the Activity with the new locale.
        if (previous != lang) {
            (context as? Activity)?.recreate()
        }
    }
}
