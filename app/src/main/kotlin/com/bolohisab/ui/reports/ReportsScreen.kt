package com.bolohisab.ui.reports

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.data.CustomerBalance
import com.bolohisab.nlu.Period
import com.bolohisab.ui.components.Avatar
import com.bolohisab.ui.components.BarChart
import com.bolohisab.ui.components.BarSlice
import com.bolohisab.ui.components.StatCard
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.theme.LedgerTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pdfFailed = stringResource(R.string.reports_pdf_failed)

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ReportsEvent.PdfReady -> {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", event.file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
                ReportsEvent.PdfFailed -> scope.launch { snackbar.showSnackbar(pdfFailed) }
            }
        }
    }

    val periodLabel = state.period.label()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.reports_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.fillMaxSize().widthIn(max = 640.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { PeriodSelector(state.period, viewModel::onPeriod) }
                item { SummaryCards(state) }
                item { SummaryChart(state) }
                item {
                    Button(
                        onClick = { viewModel.exportPdf(periodLabel) },
                        enabled = !state.exporting,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (state.exporting) {
                            CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(stringResource(R.string.reports_export_pdf))
                    }
                }
                item {
                    Text(
                        stringResource(R.string.reports_top_debtors),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                if (!state.loading && state.topDebtors.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.answer_no_debtors),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                items(state.topDebtors, key = { it.customer.id }) { row -> DebtorRow(row) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(period: Period, onSelect: (Period) -> Unit) {
    val periods = Period.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
        periods.forEachIndexed { i, p ->
            SegmentedButton(
                selected = period == p,
                onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(i, periods.size),
            ) { Text(p.label(), maxLines = 1) }
        }
    }
}

@Composable
private fun SummaryCards(state: ReportsUiState) {
    val c = LedgerTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(stringResource(R.string.stat_sales), state.summary.sales, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
        StatCard(stringResource(R.string.stat_credit), state.summary.creditGiven, c.dueContainer, c.due, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(stringResource(R.string.stat_collected), state.summary.collected, c.paidContainer, c.paid, Modifier.weight(1f))
        StatCard(stringResource(R.string.stat_expense), state.summary.expenses, c.expenseContainer, c.expense, Modifier.weight(1f))
    }
    Text(
        stringResource(R.string.reports_entry_count, Bn.number(state.summary.entries.toLong())),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun SummaryChart(state: ReportsUiState) {
    val c = LedgerTheme.colors
    val s = state.summary
    val max = maxOf(s.sales.value, s.creditGiven.value, s.collected.value, s.expenses.value).coerceAtLeast(1)
    val slices = listOf(
        BarSlice(stringResource(R.string.stat_sales), Bn.taka(s.sales), s.sales.value.toFloat() / max, MaterialTheme.colorScheme.primary),
        BarSlice(stringResource(R.string.stat_credit), Bn.taka(s.creditGiven), s.creditGiven.value.toFloat() / max, c.due),
        BarSlice(stringResource(R.string.stat_collected), Bn.taka(s.collected), s.collected.value.toFloat() / max, c.paid),
        BarSlice(stringResource(R.string.stat_expense), Bn.taka(s.expenses), s.expenses.value.toFloat() / max, c.expense),
    )
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BarChart(slices, Modifier.padding(18.dp))
    }
}

@Composable
private fun DebtorRow(row: CustomerBalance) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(row.customer.name, photoPath = row.customer.photoPath)
        Text(row.customer.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(Bn.taka(row.due), style = MaterialTheme.typography.titleMedium, color = LedgerTheme.colors.due)
    }
}

@Composable
private fun Period.label(): String = stringResource(
    when (this) {
        Period.TODAY -> R.string.period_today
        Period.YESTERDAY -> R.string.period_yesterday
        Period.THIS_WEEK -> R.string.period_week
        Period.THIS_MONTH -> R.string.period_month
    },
)
