package com.bolohisab.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bolohisab.R
import com.bolohisab.nlu.Period
import com.bolohisab.nlu.typing.TypingContext
import com.bolohisab.ui.components.VoiceOutlinedTextField
import com.bolohisab.ui.components.rememberSpeaker
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.theme.LedgerTheme
import com.bolohisab.ui.theme.MoneyStyle

/** Typed entry: the same parser as voice, for noisy shops or when the model is not installed. */
@Composable
fun TypeEntryDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.type_entry)) },
        text = {
            VoiceOutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.type_entry_hint)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null) },
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit(text) }),
                typing = TypingContext.SENTENCE,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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

/** Answer to a spoken question such as "রহিমের কত বাকি?", shown and, when [speak] is on, read aloud. */
@Composable
fun AnswerDialog(answer: Answer, onDismiss: () -> Unit, speak: Boolean = false) {
    val spoken = when (answer) {
        is Answer.Due ->
            if (answer.due.value > 0) stringResource(R.string.answer_due, answer.name, Bn.taka(answer.due))
            else stringResource(R.string.answer_no_due, answer.name)
        Answer.UnknownCustomer -> stringResource(R.string.answer_unknown_customer)
        is Answer.Sales -> stringResource(
            R.string.answer_sales,
            answer.period.label(),
            Bn.taka(answer.sales),
            Bn.taka(answer.credit),
            Bn.taka(answer.collected),
        )
        is Answer.TopDebtors ->
            if (answer.rows.isEmpty()) stringResource(R.string.answer_no_debtors)
            else stringResource(R.string.answer_top_debtors) + " " +
                answer.rows.joinToString(", ") { (name, due) -> name + " " + Bn.taka(due) }
    }
    val speaker = if (speak) rememberSpeaker(Bn.language) else null
    LaunchedEffect(answer, speaker) { speaker?.speak(spoken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.answer_title)) },
        text = {
            when (answer) {
                is Answer.Due -> Text(
                    if (answer.due.value > 0) stringResource(R.string.answer_due, answer.name, Bn.taka(answer.due))
                    else stringResource(R.string.answer_no_due, answer.name),
                    style = MaterialTheme.typography.titleLarge,
                )
                Answer.UnknownCustomer -> Text(stringResource(R.string.answer_unknown_customer))
                is Answer.Sales -> Text(
                    stringResource(
                        R.string.answer_sales,
                        answer.period.label(),
                        Bn.taka(answer.sales),
                        Bn.taka(answer.credit),
                        Bn.taka(answer.collected),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                is Answer.TopDebtors -> if (answer.rows.isEmpty()) {
                    Text(stringResource(R.string.answer_no_debtors))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.answer_top_debtors), style = MaterialTheme.typography.titleSmall)
                        answer.rows.forEach { (name, due) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    Bn.taka(due),
                                    style = MaterialTheme.typography.titleMedium.merge(MoneyStyle),
                                    color = LedgerTheme.colors.due,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = speaker?.let { s ->
            {
                TextButton(onClick = { s.speak(spoken) }) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.answer_listen))
                }
            }
        },
    )
}

