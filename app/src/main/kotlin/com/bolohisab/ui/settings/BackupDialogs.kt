package com.bolohisab.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.bolohisab.R

private const val MIN_PASSPHRASE_LENGTH = 4

/** A passphrase entry step, used both to set a new backup passphrase and to type one back in to restore. */
@Composable
fun PassphraseDialog(
    title: String,
    mismatch: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val tooShort = text.isNotEmpty() && text.length < MIN_PASSPHRASE_LENGTH

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(mismatch) { if (mismatch) text = "" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.backup_passphrase_placeholder)) },
                    leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = mismatch || tooShort,
                    supportingText = {
                        Text(
                            when {
                                mismatch -> stringResource(R.string.backup_passphrase_mismatch)
                                else -> stringResource(R.string.backup_passphrase_min)
                            },
                        )
                    },
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = text.length >= MIN_PASSPHRASE_LENGTH) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun RestoreWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.backup_restore_warning_title)) },
        text = { Text(stringResource(R.string.backup_restore_warning_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_continue), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
