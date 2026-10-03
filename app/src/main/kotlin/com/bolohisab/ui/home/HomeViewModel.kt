package com.bolohisab.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.LedgerEntry
import com.bolohisab.data.LedgerRepository
import com.bolohisab.data.Summary
import com.bolohisab.nlu.Period
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val today: Summary = Summary.EMPTY,
    val recent: List<LedgerEntry> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(repository: LedgerRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(repository.summary(Period.TODAY), repository.recentEntries(limit = 50)) { today, recent ->
            HomeUiState(today, recent, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
