package com.bolohisab.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.LearningRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Supplies [VoiceOutlinedTextField]s with Banglish → Bangla suggestions built from the built-in
 * Bolo Hisab vocabulary, this shop's customers, products and past item names, and what its typing
 * has taught the app. Rebuilt off the main thread whenever those change; null while the feature
 * is switched off in Settings.
 */
@HiltViewModel
class TypingAssistViewModel @Inject constructor(
    repository: LedgerRepository,
    settings: DisplaySettingsRepository,
    private val learning: LearningRepository,
) : ViewModel() {

    val suggester: StateFlow<PhoneticSuggester?> = combine(
        settings.banglishTyping,
        repository.customers,
        repository.products,
        repository.itemNames,
        learning.memory,
    ) { enabled, customers, products, items, memory ->
        if (!enabled) {
            null
        } else {
            PhoneticSuggester(
                TypingDictionary.forShop(
                    customers = customers.map { it.name },
                    products = products.map { it.name },
                    pastItems = items,
                    memory = memory,
                ),
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Remembers that the shopkeeper turned the Banglish [typed] into [text] (which may be [typed] itself). */
    fun recordChoice(typed: String, text: String) {
        viewModelScope.launch { learning.recordChoice(typed, text) }
    }
}
