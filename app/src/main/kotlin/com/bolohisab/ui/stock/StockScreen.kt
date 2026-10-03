package com.bolohisab.ui.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.data.Product
import com.bolohisab.ui.components.VoiceOutlinedTextField
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.theme.LedgerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(viewModel: StockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProductDialogState>(ProductDialogState.Hidden) }
    var restocking by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stock_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ProductDialogState.Adding }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.stock_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 640.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    VoiceOutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQuery,
                        placeholder = { Text(stringResource(R.string.stock_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (!state.loading && state.productCount == 0) {
                    item {
                        Text(
                            stringResource(R.string.stock_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                items(state.products, key = { it.id }) { product ->
                    ProductRow(
                        product,
                        onClick = { editing = ProductDialogState.Editing(product) },
                        onRestock = { restocking = product },
                        modifier = Modifier.animateItem(),
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    when (val step = editing) {
        ProductDialogState.Hidden -> Unit
        ProductDialogState.Adding -> ProductDialog(
            editing = null,
            onSubmit = { name, unit, opening, threshold ->
                viewModel.onAddProduct(name, unit, opening, threshold)
                editing = ProductDialogState.Hidden
            },
            onDismiss = { editing = ProductDialogState.Hidden },
        )
        is ProductDialogState.Editing -> ProductDialog(
            editing = step.product,
            onSubmit = { name, unit, _, threshold ->
                viewModel.onUpdateProduct(step.product.id, name, unit, threshold)
                editing = ProductDialogState.Hidden
            },
            onDismiss = { editing = ProductDialogState.Hidden },
        )
    }

    restocking?.let { product ->
        RestockDialog(
            product = product,
            onSubmit = { qty -> viewModel.onRestock(product.id, qty); restocking = null },
            onDismiss = { restocking = null },
        )
    }
}

private sealed interface ProductDialogState {
    data object Hidden : ProductDialogState
    data object Adding : ProductDialogState
    data class Editing(val product: Product) : ProductDialogState
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit, onRestock: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(if (product.isLow) c.attentionContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (product.isLow) Icons.Rounded.Warning else Icons.Rounded.Inventory2,
                contentDescription = if (product.isLow) stringResource(R.string.stock_low_label) else null,
                tint = if (product.isLow) c.attention else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Bn.qty(product.stockQty)}${product.unit?.let { " ${it.label}" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (product.isLow) c.attention else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestock) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.stock_restock))
        }
    }
}
