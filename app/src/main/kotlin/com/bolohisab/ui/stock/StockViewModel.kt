package com.bolohisab.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.LedgerRepository
import com.bolohisab.data.Product
import com.bolohisab.nlu.QuantityUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockUiState(
    val query: String = "",
    val products: List<Product> = emptyList(),
    val productCount: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class StockViewModel @Inject constructor(private val repository: LedgerRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<StockUiState> =
        combine(repository.products, query) { all, q ->
            val sorted = all.sortedWith(compareByDescending<Product> { it.isLow }.thenBy { it.name })
            StockUiState(
                query = q,
                products = if (q.isBlank()) sorted else sorted.filter { it.name.contains(q.trim(), ignoreCase = true) },
                productCount = all.size,
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun onQuery(q: String) { query.value = q }

    fun onAddProduct(name: String, unit: QuantityUnit?, openingStock: Double, lowStockThreshold: Double?) {
        viewModelScope.launch { repository.addProduct(name, unit, openingStock, lowStockThreshold) }
    }

    fun onUpdateProduct(id: Long, name: String, unit: QuantityUnit?, lowStockThreshold: Double?) {
        viewModelScope.launch { repository.updateProduct(id, name, unit, lowStockThreshold) }
    }

    fun onRestock(id: Long, qty: Double) {
        viewModelScope.launch { repository.restock(id, qty) }
    }
}
