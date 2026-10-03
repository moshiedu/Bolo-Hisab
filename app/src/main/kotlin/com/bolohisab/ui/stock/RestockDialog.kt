package com.bolohisab.ui.stock

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.bolohisab.ui.format.Bn

@Composable
fun RestockDialog(product: Product, onSubmit: (qty: Double) -> Unit, onDismiss: () -> Unit) {
    var qty by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                label = { Text(stringResource(R.string.stock_restock_qty)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                suffix = product.unit?.let { u -> { Text(u.label) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { Bn.parseAmount(qty)?.takeIf { it > 0 }?.let(onSubmit) },
                enabled = (Bn.parseAmount(qty) ?: 0.0) > 0,
            ) { Text(stringResource(R.string.stock_restock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
