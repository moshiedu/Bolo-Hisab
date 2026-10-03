package com.bolohisab.ui.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.data.CustomerBalance
import com.bolohisab.nlu.typing.TypingContext
import com.bolohisab.ui.components.Avatar
import com.bolohisab.ui.components.VoiceOutlinedTextField
import com.bolohisab.ui.components.animatedTaka
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.theme.LedgerTheme
import com.bolohisab.ui.theme.MoneyStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(onOpen: (Long) -> Unit, viewModel: CustomersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var addingCustomer by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_customers)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingCustomer = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.customer_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 640.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { TotalDueCard(state) }
                item {
                    VoiceOutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQuery,
                        placeholder = { Text(stringResource(R.string.customers_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        typing = TypingContext.CUSTOMER,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (!state.loading && state.customerCount == 0) {
                    item {
                        Text(
                            stringResource(R.string.customers_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                items(state.rows, key = { it.customer.id }) { row ->
                    CustomerRow(
                        row,
                        onClick = { onOpen(row.customer.id) },
                        onCall = {
                            row.customer.phone?.let {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (addingCustomer) {
        CustomerDialog(
            editing = null,
            onSubmit = { name, phone, address, photoPath ->
                viewModel.onAddCustomer(name, phone, address, photoPath)
                addingCustomer = false
            },
            onDismiss = { addingCustomer = false },
        )
    }
}

@Composable
private fun TotalDueCard(state: CustomersUiState) {
    val c = LedgerTheme.colors
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = c.dueContainer,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.customers_total_due), style = MaterialTheme.typography.labelLarge, color = c.due)
                Text(
                    animatedTaka(state.totalDue),
                    style = MaterialTheme.typography.headlineMedium.merge(MoneyStyle),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(R.string.customers_count, Bn.number(state.customerCount.toLong())),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomerRow(row: CustomerBalance, onClick: () -> Unit, onCall: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    val owes = row.due.value > 0
    val hasPhone = row.customer.phone != null
    Row(
        modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = if (hasPhone) onCall else null)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(row.customer.name, photoPath = row.customer.photoPath)
        Column(Modifier.weight(1f)) {
            Text(row.customer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.lastActivity?.let { stringResource(R.string.customer_last_activity, Bn.day(it)) }
                    ?: stringResource(R.string.customer_no_activity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val address = row.customer.address
            if (!address.isNullOrBlank()) {
                Text(
                    address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (owes) Bn.taka(row.due) else stringResource(R.string.customer_settled),
            style = (if (owes) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge).merge(MoneyStyle),
            color = if (owes) c.due else c.paid,
        )
    }
}
