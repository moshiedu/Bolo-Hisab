package com.bolohisab.ui.record

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bolohisab.R
import com.bolohisab.nlu.EntryType
import com.bolohisab.nlu.Field
import com.bolohisab.nlu.KnownCustomer
import com.bolohisab.nlu.typing.TypingContext
import com.bolohisab.ui.components.VoiceOutlinedTextField
import com.bolohisab.ui.components.formWidth
import com.bolohisab.ui.components.label
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.theme.LedgerTheme
import com.bolohisab.ui.theme.MoneyStyle
import kotlinx.coroutines.launch

/**
 * The confirm card. Every parsed entry lands here; fields the parser was unsure of
 * are tinted amber so the shopkeeper checks them before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    review: ReviewState,
    customers: List<KnownCustomer>,
    onChange: (ReviewState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .formWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(review)
                TypeSelector(review, onChange)
                if (review.type != EntryType.EXPENSE) CustomerField(review, customers, onChange)
                if (review.showsItems) ItemsEditor(review, onChange)
                AmountFields(review, onChange)
                if (review.history.isNotEmpty()) HistorySection(review)
                Footer(review, onSave, onDismiss, onDelete)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Header(review: ReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
        if (review.transcript.isNotBlank()) {
            Text(
                "“${review.transcript}”",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(review: ReviewState, onChange: (ReviewState) -> Unit) {
    val types = EntryType.entries
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = review.type == t,
                    onClick = { onChange(review.withType(t)) },
                    shape = SegmentedButtonDefaults.itemShape(i, types.size),
                ) { Text(t.label(), maxLines = 1) }
            }
        }
        if (review.isFlagged(Field.TYPE)) CheckHint()
    }
}

@Composable
private fun CustomerField(review: ReviewState, customers: List<KnownCustomer>, onChange: (ReviewState) -> Unit) {
    val flagged = review.isFlagged(Field.CUSTOMER)
    val missing = review.needsCustomer && review.customerName.isBlank()
    val context = LocalContext.current
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    .takeIf { it >= 0 }?.let(cursor::getString)
                val number = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    .takeIf { it >= 0 }?.let(cursor::getString)
                if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                    onChange(review.withContactPicked(name, number, customers))
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VoiceOutlinedTextField(
            value = review.customerName,
            onValueChange = { onChange(review.withCustomer(it, customers)) },
            label = { Text(stringResource(R.string.review_customer)) },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            singleLine = true,
            typing = TypingContext.CUSTOMER,
            isError = missing,
            colors = fieldColors(flagged),
            trailingContent = {
                if (review.isNewCustomer) {
                    Icon(Icons.Rounded.PersonAddAlt, contentDescription = stringResource(R.string.review_customer_new))
                }
                IconButton(onClick = {
                    pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }) {
                    Icon(Icons.Rounded.Contacts, contentDescription = stringResource(R.string.customer_pick_contact))
                }
            },
            supportingText = when {
                missing -> { { Text(stringResource(R.string.review_need_customer)) } }
                review.isNewCustomer -> { { Text(stringResource(R.string.review_customer_new)) } }
                flagged -> { { Text(stringResource(R.string.review_check)) } }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val query = review.customerName.trim()
        val near = review.nearMatch(customers)
        val suggestions = (listOfNotNull(near) + customers
            .filter { query.isNotEmpty() && it.name.contains(query, ignoreCase = true) && it.id != review.matchedCustomerId })
            .distinctBy { it.id }
            .take(5)
        AnimatedVisibility(
            visible = suggestions.isNotEmpty(),
            enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(100)),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { c ->
                    SuggestionChip(onClick = { onChange(review.withCustomer(c.name, customers)) }, label = { Text(c.name) })
                }
            }
        }
    }
}

@Composable
private fun ItemsEditor(review: ReviewState, onChange: (ReviewState) -> Unit) {
    val flagged = review.isFlagged(Field.ITEMS)
    Column(
        Modifier.animateContentSize(spring(dampingRatio = 0.8f)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.review_items), style = MaterialTheme.typography.titleSmall)
        review.items.forEachIndexed { index, item ->
            fun update(edit: ItemEdit) = onChange(
                review.copy(items = review.items.map { if (it.key == item.key) edit else it }, uncertain = review.uncertain - Field.ITEMS),
            )
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    VoiceOutlinedTextField(
                        value = item.name,
                        onValueChange = { update(item.copy(name = it)) },
                        placeholder = { Text(stringResource(R.string.review_item_name)) },
                        leadingIcon = { Icon(Icons.Rounded.ShoppingBag, contentDescription = null) },
                        singleLine = true,
                        typing = TypingContext.ITEM,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange(review.copy(items = review.items - item)) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.review_remove_item))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = item.quantity,
                        onValueChange = { update(item.copy(quantity = it)) },
                        placeholder = { Text(stringResource(R.string.review_item_qty)) },
                        suffix = if (item.unit != null) {
                            { Text(item.unit.label, style = MaterialTheme.typography.bodySmall) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = item.price,
                        onValueChange = { update(item.copy(price = it)) },
                        placeholder = { Text(stringResource(R.string.review_item_price)) },
                        prefix = { Text("৳") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.merge(MoneyStyle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors(flagged && item.price.isBlank()),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        TextButton(onClick = {
            val key = (review.items.maxOfOrNull { it.key } ?: -1) + 1
            onChange(review.copy(items = review.items + ItemEdit(key, "", "", null, "")))
        }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.review_add_item), Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun AmountFields(review: ReviewState, onChange: (ReviewState) -> Unit) {
    val itemsPriced = review.showsItems && review.items.any { Bn.parseAmount(it.price) != null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (itemsPriced && review.totalOverride != null) {
            // The spoken total disagrees with the item prices (usually a discount): show both and
            // let the shopkeeper keep it or snap back to the item sum.
            OutlinedTextField(
                value = review.totalOverride,
                onValueChange = { onChange(review.copy(totalOverride = it, uncertain = review.uncertain - Field.TOTAL)) },
                label = { Text(stringResource(R.string.review_total)) },
                prefix = { Text("৳") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.merge(MoneyStyle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors(review.isFlagged(Field.TOTAL)),
                supportingText = { Text(stringResource(R.string.review_items_sum, Bn.taka(review.itemSum))) },
                trailingIcon = {
                    IconButton(onClick = { onChange(review.copy(totalOverride = null, uncertain = review.uncertain - Field.TOTAL)) }) {
                        Icon(Icons.Rounded.Restore, contentDescription = stringResource(R.string.review_use_items_sum))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (itemsPriced) {
            SummaryLine(stringResource(R.string.review_total), Bn.taka(review.total), emphasised = true)
        } else {
            val flagged = review.isFlagged(Field.AMOUNT) || review.isFlagged(Field.TOTAL)
            OutlinedTextField(
                value = review.amount,
                onValueChange = { onChange(review.copy(amount = it, uncertain = review.uncertain - Field.AMOUNT - Field.TOTAL)) },
                label = { Text(stringResource(if (review.showsItems) R.string.review_total else R.string.review_amount)) },
                prefix = { Text("৳") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.merge(MoneyStyle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors(flagged),
                supportingText = if (flagged) { { Text(stringResource(R.string.review_check)) } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (review.type == EntryType.CREDIT_SALE) {
            val flagged = review.isFlagged(Field.PAID)
            OutlinedTextField(
                value = review.paid,
                onValueChange = { onChange(review.copy(paid = it, uncertain = review.uncertain - Field.PAID)) },
                label = { Text(stringResource(R.string.review_paid)) },
                prefix = { Text("৳") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(MoneyStyle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors(flagged),
                isError = ReviewProblem.PAID_TOO_MUCH in review.problems,
                supportingText = if (ReviewProblem.PAID_TOO_MUCH in review.problems) {
                    { Text(stringResource(R.string.review_paid_too_much)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            SummaryLine(stringResource(R.string.review_due), Bn.taka(review.due), emphasised = true, isDue = true)
        }
        if (review.type == EntryType.EXPENSE || !review.note.isNullOrBlank()) {
            VoiceOutlinedTextField(
                value = review.note.orEmpty(),
                onValueChange = { onChange(review.copy(note = it)) },
                label = { Text(stringResource(R.string.review_note)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, emphasised: Boolean, isDue: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(tween(150)) + slideInVertically(tween(150)) { h -> h / 3 })
                    .togetherWith(fadeOut(tween(100)) + slideOutVertically(tween(100)) { h -> -h / 3 })
            },
            label = "summaryValue",
        ) { v ->
            Text(
                v,
                style = (if (emphasised) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium).merge(MoneyStyle),
                color = if (isDue) LedgerTheme.colors.due else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HistorySection(review: ReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.review_history_title), style = MaterialTheme.typography.titleSmall)
        review.history.forEach { snapshot ->
            Text(
                stringResource(R.string.review_history_row, Bn.day(snapshot.changedAt), Bn.taka(snapshot.total)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Footer(review: ReviewState, onSave: () -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val problems = review.problems
    val ready = problems.isEmpty()
    val editing = review.editingEntryId != null
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        tween(200),
        label = "footerContainer",
    )
    val contentColor by animateColorAsState(
        if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(200),
        label = "footerContent",
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ReviewProblem.NEED_AMOUNT in problems) {
            Text(
                stringResource(R.string.review_need_amount),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    if (ready) {
                        onSave()
                    } else {
                        scope.launch {
                            shake.animateTo(1f, tween(60))
                            shake.animateTo(-1f, tween(60))
                            shake.animateTo(1f, tween(60))
                            shake.animateTo(0f, tween(60))
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp)
                    .graphicsLayer { translationX = shake.value * 10f },
            ) {
                Text(
                    stringResource(if (editing) R.string.action_update else R.string.action_save),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (editing && onDelete != null) {
            TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.review_delete_confirm_title)) },
            text = { Text(stringResource(R.string.review_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete?.invoke() }) {
                    Text(stringResource(R.string.review_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CheckHint() = Text(
    stringResource(R.string.review_check),
    style = MaterialTheme.typography.bodySmall,
    color = LedgerTheme.colors.attention,
)

/** Amber outline and tint for a field the parser was unsure of; fades in rather than cutting in. */
@Composable
private fun fieldColors(flagged: Boolean): TextFieldColors {
    val c = LedgerTheme.colors
    val progress by animateFloatAsState(if (flagged) 1f else 0f, tween(220), label = "fieldFlag")
    if (progress == 0f) return OutlinedTextFieldDefaults.colors()
    return OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = lerp(MaterialTheme.colorScheme.outline, c.attention, progress),
        focusedBorderColor = lerp(MaterialTheme.colorScheme.primary, c.attention, progress),
        unfocusedContainerColor = c.attentionContainer.copy(alpha = 0.45f * progress),
        focusedContainerColor = c.attentionContainer.copy(alpha = 0.25f * progress),
        unfocusedSupportingTextColor = lerp(MaterialTheme.colorScheme.onSurfaceVariant, c.attention, progress),
        focusedSupportingTextColor = lerp(MaterialTheme.colorScheme.onSurfaceVariant, c.attention, progress),
    )
}
