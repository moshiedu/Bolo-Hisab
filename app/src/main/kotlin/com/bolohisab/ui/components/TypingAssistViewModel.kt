package com.bolohisab.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.LedgerRepository
import com.bolohisab.data.settings.DisplaySettingsRepository
import com.bolohisab.nlu.typing.PhoneticSuggester
import com.bolohisab.nlu.typing.TypingDictionary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Supplies [VoiceOutlinedTextField]s with Banglish → Bangla suggestions built from the built-in
 * Bolo Hisab vocabulary plus this shop's customers, products and past item names. Rebuilt off the
 * main thread whenever those change; null while the feature is switched off in Settings.
 */
@HiltViewModel
class TypingAssistViewModel @Inject constructor(
    repository: LedgerRepository,
    settings: DisplaySettingsRepository,
) : ViewModel() {

    val suggester: StateFlow<PhoneticSuggester?> = combine(
        settings.banglishTyping,
        repository.customers,
        repository.products,
        repository.itemNames,
    ) { enabled, customers, products, items ->
        if (!enabled) {
            null
        } else {
            PhoneticSuggester(
                TypingDictionary.forShop(
                    customers = customers.map { it.name },
                    products = products.map { it.name },
                    pastItems = items,
                ),
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
