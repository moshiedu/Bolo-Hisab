package com.bolohisab.ui.stock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.bolohisab.R
import com.bolohisab.data.Product
import com.bolohisab.nlu.QuantityUnit
import com.bolohisab.nlu.typing.TypingContext
import com.bolohisab.ui.components.VoiceOutlinedTextField
import com.bolohisab.ui.format.Bn

/** Adds a new tracked product, or edits an existing one's name/unit/threshold (never its stock level directly — that's [RestockDialog]'s job). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDialog(
    editing: Product?,
    onSubmit: (name: String, unit: QuantityUnit?, openingStock: Double, lowStockThreshold: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var unit by remember { mutableStateOf(editing?.unit) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var openingStock by rememberSaveable { mutableStateOf(editing?.let { Bn.qty(it.stockQty) }.orEmpty()) }
    var threshold by rememberSaveable { mutableStateOf(editing?.lowStockThreshold?.let(Bn::qty).orEmpty()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing != null) R.string.stock_edit else R.string.stock_add)) },
        text = {
            Column {
                VoiceOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.stock_name)) },
                    leadingIcon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
                    singleLine = true,
                    typing = TypingContext.ITEM,
                    modifier = Modifier.focusRequester(focus).fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = unitMenuOpen, onExpandedChange = { unitMenuOpen = it }) {
                    OutlinedTextField(
                        value = unit?.label ?: stringResource(R.string.stock_unit_none),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.stock_unit)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuOpen) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stock_unit_none)) },
                            onClick = { unit = null; unitMenuOpen = false },
                        )
                        QuantityUnit.entries.forEach { u ->
                            DropdownMenuItem(text = { Text(u.label) }, onClick = { unit = u; unitMenuOpen = false })
                        }
                    }
                }
                if (editing == null) {
                    OutlinedTextField(
                        value = openingStock,
                        onValueChange = { openingStock = it },
                        label = { Text(stringResource(R.string.stock_opening_qty)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(R.string.stock_low_threshold)) },
                    leadingIcon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
                    supportingText = { Text(stringResource(R.string.stock_low_threshold_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(name.trim(), unit, Bn.parseAmount(openingStock) ?: 0.0, Bn.parseAmount(threshold))
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
