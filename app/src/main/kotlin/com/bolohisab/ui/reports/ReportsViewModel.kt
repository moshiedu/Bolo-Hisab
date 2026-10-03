package com.bolohisab.ui.reports

import android.content.Context
import com.bolohisab.data.CustomerBalance
import com.bolohisab.data.LedgerRepository
import com.bolohisab.data.Summary
import com.bolohisab.nlu.Period
import com.bolohisab.ui.format.Bn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class ReportsUiState(
    val period: Period = Period.TODAY,
    val summary: Summary = Summary.EMPTY,
    val topDebtors: List<CustomerBalance> = emptyList(),
    val loading: Boolean = true,
    val exporting: Boolean = false,
)

sealed interface ReportsEvent {
    data class PdfReady(val file: File) : ReportsEvent
    data object PdfFailed : ReportsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: LedgerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val period = MutableStateFlow(Period.TODAY)
    private val exporting = MutableStateFlow(false)
    private val events = Channel<ReportsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<ReportsUiState> = period.flatMapLatest { p ->
        combine(repository.summary(p), repository.customerBalances(), exporting) { summary, balances, working ->
            ReportsUiState(
                period = p,
                summary = summary,
                topDebtors = balances.filter { it.due.value > 0 }.take(5),
                loading = false,
                exporting = working,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun onPeriod(p: Period) {
        period.value = p
    }

    fun exportPdf(periodLabel: String) {
        val snapshot = state.value
        exporting.value = true
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    PdfReportGenerator.generate(
                        context = context,
                        periodLabel = periodLabel,
                        generatedAt = Bn.longDate(LocalDate.now()),
                        summary = snapshot.summary,
                        topDebtors = snapshot.topDebtors,
                    )
                }.getOrNull()
            }
            exporting.value = false
            events.send(if (file != null) ReportsEvent.PdfReady(file) else ReportsEvent.PdfFailed)
        }
    }
}
