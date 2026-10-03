package com.bolohisab.ui.customers

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bolohisab.data.CustomerBalance
import com.bolohisab.data.LedgerEntry
import com.bolohisab.data.LedgerRepository
import com.bolohisab.nlu.Poisha
import com.bolohisab.ui.navigation.CustomerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersUiState(
    val query: String = "",
    val rows: List<CustomerBalance> = emptyList(),
    val totalDue: Poisha = Poisha.ZERO,
    val customerCount: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class CustomersViewModel @Inject constructor(private val repository: LedgerRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<CustomersUiState> =
        combine(repository.customerBalances(), query) { all, q ->
            CustomersUiState(
                query = q,
                rows = if (q.isBlank()) all else all.filter { it.customer.name.contains(q.trim(), ignoreCase = true) },
                totalDue = Poisha(all.sumOf { maxOf(it.due.value, 0L) }),
                customerCount = all.size,
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomersUiState())

    fun onQuery(q: String) { query.value = q }

    fun onAddCustomer(name: String, phone: String?, address: String?, photoPath: String?) {
        viewModelScope.launch { repository.addCustomer(name, phone?.takeIf { it.isNotBlank() }, address, photoPath) }
    }
}

data class CustomerDetailUiState(
    val balance: CustomerBalance? = null,
    val entries: List<LedgerEntry> = emptyList(),
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: LedgerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val customerId = savedState.toRoute<CustomerRoute>().id

    val state: StateFlow<CustomerDetailUiState> =
        combine(repository.customerBalance(customerId), repository.entriesFor(customerId)) { b, e ->
            CustomerDetailUiState(b, e)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerDetailUiState())

    fun onUpdateCustomer(name: String, phone: String?, address: String?, photoPath: String?) {
        viewModelScope.launch {
            val oldPhotoPath = state.value.balance?.customer?.photoPath
            if (oldPhotoPath != null && oldPhotoPath != photoPath) CustomerPhotoStore.delete(oldPhotoPath)
            repository.updateCustomer(customerId, name, phone?.takeIf { it.isNotBlank() }, address, photoPath)
        }
    }
}
