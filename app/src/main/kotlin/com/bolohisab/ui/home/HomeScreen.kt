package com.bolohisab.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Keyboard
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.data.Summary
import com.bolohisab.ui.components.EntryRow
import com.bolohisab.ui.components.StatCard
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.record.AnswerDialog
import com.bolohisab.ui.record.ListeningCard
import com.bolohisab.ui.record.MicButton
import com.bolohisab.ui.record.MicState
import com.bolohisab.ui.record.RecordEvent
import com.bolohisab.ui.record.RecordViewModel
import com.bolohisab.ui.record.ReviewSheet
import com.bolohisab.ui.record.TypeEntryDialog
import com.bolohisab.ui.theme.LedgerTheme
import com.bolohisab.voice.ModelState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    recordViewModel: RecordViewModel = hiltViewModel(),
) {
    val home by homeViewModel.state.collectAsStateWithLifecycle()
    val record by recordViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var typing by rememberSaveable { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) typing = true
    }
    fun hasMic() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val saved = stringResource(R.string.saved)
    val entryUpdated = stringResource(R.string.entry_updated)
    val undo = stringResource(R.string.action_undo)
    val undone = stringResource(R.string.undone)
    val holdHint = stringResource(R.string.mic_tap_hint)
    val nothingHeard = stringResource(R.string.nothing_heard)
    val modelMissing = stringResource(R.string.model_missing)
    val notUnderstood = stringResource(R.string.not_understood, "%s")
    val failed = stringResource(R.string.save_failed, "%s")
    val micBusy = stringResource(R.string.mic_busy)

    LaunchedEffect(Unit) {
        recordViewModel.eventFlow.collect { event ->
            when (event) {
                is RecordEvent.Saved -> {
                    val r = snackbar.showSnackbar(saved, actionLabel = undo, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) {
                        recordViewModel.onUndo(event.entryId)
                        snackbar.showSnackbar(undone)
                    }
                }
                is RecordEvent.Updated -> snackbar.showSnackbar(entryUpdated)
                is RecordEvent.EntryDeleted -> {
                    val r = snackbar.showSnackbar(undone, actionLabel = undo, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) recordViewModel.onRestoreDeleted(event.entryId)
                }
                is RecordEvent.NotUnderstood -> snackbar.showSnackbar(notUnderstood.format(event.transcript))
                RecordEvent.NothingHeard -> snackbar.showSnackbar(nothingHeard)
                RecordEvent.HoldToTalk -> snackbar.showSnackbar(holdHint)
                RecordEvent.ModelUnavailable -> { snackbar.showSnackbar(modelMissing); typing = true }
                RecordEvent.MicBusy -> snackbar.showSnackbar(micBusy)
                is RecordEvent.Failed -> snackbar.showSnackbar(failed.format(event.message))
            }
        }
    }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 112.dp)) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            Bn.longDate(LocalDate.now()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { typing = true }) {
                        Icon(Icons.Rounded.Keyboard, contentDescription = stringResource(R.string.type_entry))
                    }
                    IconButton(onClick = recordViewModel::onManualEntry) {
                        Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.review_title))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 150.dp), modifier = Modifier.fillMaxSize()) {
                item { SummaryRow(home.today) }
                item { ModelBanner(record.model) }
                item {
                    Text(
                        stringResource(R.string.recent_entries),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                if (!home.loading && home.recent.isEmpty()) {
                    item { EmptyState(onExample = recordViewModel::onTyped) }
                }
                items(home.recent, key = { it.id }) { entry ->
                    EntryRow(entry, modifier = Modifier.animateItem(), onClick = { recordViewModel.onEditEntry(entry) })
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            val listening = record.mic as? MicState.Listening
            AnimatedVisibility(visible = listening != null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = listening != null,
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 3 },
                ) {
                    ListeningCard(listening?.partial.orEmpty(), listening?.level ?: 0f, Modifier.padding(bottom = 16.dp))
                }
                MicButton(
                    listening = listening != null,
                    level = listening?.level ?: 0f,
                    onDown = { if (hasMic()) recordViewModel.onMicDown() else permission.launch(Manifest.permission.RECORD_AUDIO) },
                    onUp = recordViewModel::onMicUp,
                    onAccessibilityClick = { typing = true },
                )
                if (listening == null) {
                    Text(
                        stringResource(R.string.mic_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    record.answer?.let { AnswerDialog(it, recordViewModel::onAnswerDismiss) }
    if (typing) {
        TypeEntryDialog(
            onSubmit = { typing = false; recordViewModel.onTyped(it) },
            onDismiss = { typing = false },
        )
    }
}

@Composable
private fun SummaryRow(today: Summary) {
    val c = LedgerTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(stringResource(R.string.stat_sales), today.sales, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
        StatCard(stringResource(R.string.stat_credit), today.creditGiven, c.dueContainer, c.due, Modifier.weight(1f))
        StatCard(stringResource(R.string.stat_collected), today.collected, c.paidContainer, c.paid, Modifier.weight(1f))
    }
}

@Composable
private fun ModelBanner(state: ModelState) {
    val text = when (state) {
        ModelState.Missing -> stringResource(R.string.model_missing)
        ModelState.Loading -> stringResource(R.string.model_loading)
        is ModelState.Failed -> stringResource(R.string.model_failed, state.message)
        else -> return
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = LedgerTheme.colors.attentionContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** First run: explain the one gesture and offer tappable examples that run through the real parser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyState(onExample: (String) -> Unit) {
    val examples = listOf(
        stringResource(R.string.empty_example_1),
        stringResource(R.string.empty_example_2),
        stringResource(R.string.empty_example_3),
    )
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        examples.forEach { example ->
            Surface(
                onClick = { onExample(example) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("“$example”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
            }
        }
    }
}
