package com.bolohisab.ui.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.ui.components.Avatar
import com.bolohisab.ui.components.EntryRow
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.record.RecordEvent
import com.bolohisab.ui.record.RecordViewModel
import com.bolohisab.ui.record.ReviewSheet
import com.bolohisab.ui.theme.LedgerTheme
import com.bolohisab.ui.theme.MoneyStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
    recordViewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val record by recordViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val balance = state.balance
    val name = balance?.customer?.name.orEmpty()
    val phone = balance?.customer?.phone
    val address = balance?.customer?.address
    val photoPath = balance?.customer?.photoPath
    val due = balance?.due
    var editing by remember { mutableStateOf(false) }
    val c = LedgerTheme.colors
    val reminder = due?.let { stringResource(R.string.reminder_message, name, Bn.taka(it)) }

    val snackbar = remember { SnackbarHostState() }
    val entryUpdated = stringResource(R.string.entry_updated)
    val undo = stringResource(R.string.action_undo)
    val undone = stringResource(R.string.undone)
    val failed = stringResource(R.string.save_failed, "%s")
    LaunchedEffect(Unit) {
        recordViewModel.eventFlow.collect { event ->
            when (event) {
                is RecordEvent.Updated -> snackbar.showSnackbar(entryUpdated)
                is RecordEvent.EntryDeleted -> {
                    val r = snackbar.showSnackbar(undone, actionLabel = undo, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) recordViewModel.onRestoreDeleted(event.entryId)
                }
                is RecordEvent.Failed -> snackbar.showSnackbar(failed.format(event.message))
                else -> Unit
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (phone != null) {
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }) {
                            Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.customer_call))
                        }
                    }
                    if (balance != null) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.customer_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (photoPath != null || !address.isNullOrBlank()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Avatar(name, photoPath = photoPath, size = 56.dp)
                        if (!address.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    Icons.Rounded.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                val owes = (due?.value ?: 0) > 0
                Surface(
                    Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = if (owes) c.dueContainer else c.paidContainer,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(if (owes) R.string.type_credit_sale else R.string.customer_settled),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (owes) c.due else c.paid,
                        )
                        Text(
                            due?.let(Bn::taka).orEmpty(),
                            style = MaterialTheme.typography.headlineMedium.merge(MoneyStyle),
                        )
                        if (owes && reminder != null) {
                            Button(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, reminder)
                                    context.startActivity(Intent.createChooser(send, null))
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                                Text(stringResource(R.string.customer_send_reminder), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.customer_history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            var lastDay: String? = null
            state.entries.forEach { entry ->
                val day = Bn.day(entry.createdAt)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "day-$day") {
                        Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp)) {
                            Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item(key = entry.id) {
                    EntryRow(entry, showCustomer = false, onClick = { recordViewModel.onEditEntry(entry) })
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    record.review?.let { review ->
        ReviewSheet(
            review = review,
            customers = record.customers,
            onChange = recordViewModel::onReviewChange,
            onSave = recordViewModel::onSave,
            onDismiss = recordViewModel::onReviewDismiss,
            onDelete = recordViewModel::onDeleteEditingEntry,
        )
    }

    if (editing && balance != null) {
        CustomerDialog(
            editing = balance.customer,
            onSubmit = { n, p, a, photo -> viewModel.onUpdateCustomer(n, p, a, photo); editing = false },
            onDismiss = { editing = false },
        )
    }
}
