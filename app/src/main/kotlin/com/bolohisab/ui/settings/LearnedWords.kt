package com.bolohisab.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bolohisab.R
import com.bolohisab.data.LearnedCorrection
import com.bolohisab.data.LearningRepository
import com.bolohisab.nlu.typing.LearnedChoice
import com.bolohisab.ui.format.Bn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LearnedWordsViewModel @Inject constructor(
    private val learning: LearningRepository,
) : ViewModel() {
    val corrections: StateFlow<List<LearnedCorrection>> =
        learning.correctionList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val choices: StateFlow<List<LearnedChoice>> =
        learning.choiceList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun forgetCorrection(c: LearnedCorrection) = viewModelScope.launch { learning.forgetCorrection(c.heard) }
    fun forgetChoice(c: LearnedChoice) = viewModelScope.launch { learning.forgetChoice(c.typed, c.text) }
    fun forgetAll() = viewModelScope.launch { learning.forgetAll() }
}

/**
 * Settings row for what the app has learned from this shop, opening a sheet where each learned
 * correction or typing pick can be reviewed and removed — a wrong lesson must be easy to undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedWordsItem(viewModel: LearnedWordsViewModel = hiltViewModel()) {
    val corrections by viewModel.corrections.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    ListItem(
        leadingContent = { Icon(Icons.Rounded.Psychology, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.learned_title)) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.learned_summary,
                    Bn.digits(corrections.size.toString()),
                    Bn.digits(choices.size.toString()),
                ),
            )
        },
        modifier = Modifier.fillMaxWidth().clickable { open = true },
    )

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.learned_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmClear = true }, enabled = corrections.isNotEmpty() || choices.isNotEmpty()) {
                            Text(stringResource(R.string.learned_clear_all))
                        }
                    }
                    Text(
                        stringResource(R.string.learned_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }

                item { SectionTitle(stringResource(R.string.learned_corrections)) }
                if (corrections.isEmpty()) item { EmptyLine(stringResource(R.string.learned_corrections_empty)) }
                items(corrections, key = { "c:" + it.heard }) { c ->
                    LearnedRow(
                        from = c.heard,
                        to = c.fixed,
                        count = c.count,
                        icon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null) },
                        onForget = { viewModel.forgetCorrection(c) },
                    )
                }

                item { SectionTitle(stringResource(R.string.learned_choices)) }
                if (choices.isEmpty()) item { EmptyLine(stringResource(R.string.learned_choices_empty)) }
                items(choices, key = { "t:" + it.typed + "\u0000" + it.text }) { c ->
                    LearnedRow(
                        from = c.typed,
                        to = c.text,
                        count = c.count,
                        icon = null,
                        onForget = { viewModel.forgetChoice(c) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.learned_clear_all)) },
            text = { Text(stringResource(R.string.learned_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.forgetAll(); confirmClear = false }) {
                    Text(stringResource(R.string.learned_clear_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun LearnedRow(from: String, to: String, count: Int, icon: (@Composable () -> Unit)?, onForget: () -> Unit) {
    ListItem(
        leadingContent = icon,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(from, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                Text(to, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            }
        },
        supportingContent = { Text(stringResource(R.string.learned_times, Bn.digits(count.toString()))) },
        trailingContent = {
            IconButton(onClick = onForget) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.learned_forget))
            }
        },
    )
}
