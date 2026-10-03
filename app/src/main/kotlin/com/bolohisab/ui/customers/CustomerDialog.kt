package com.bolohisab.ui.customers

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bolohisab.R
import com.bolohisab.data.Customer
import com.bolohisab.nlu.typing.TypingContext
import com.bolohisab.ui.components.Avatar
import com.bolohisab.ui.components.VoiceOutlinedTextField
import java.io.File
import java.util.UUID

/** Adds a new customer, or edits an existing one's name/phone/address/photo. */
@Composable
fun CustomerDialog(
    editing: Customer?,
    onSubmit: (name: String, phone: String?, address: String?, photoPath: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(editing?.phone.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(editing?.address.orEmpty()) }
    var photoPath by rememberSaveable { mutableStateOf(editing?.photoPath) }
    var photoMenuOpen by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME).takeIf { it >= 0 }
                    ?.let(cursor::getString)?.let { name = it }
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER).takeIf { it >= 0 }
                    ?.let(cursor::getString)?.let { phone = it }
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoPath = CustomerPhotoStore.save(context, uri)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { photoPath = CustomerPhotoStore.save(context, it) }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    LaunchedEffect(Unit) { if (editing == null) focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing != null) R.string.customer_edit else R.string.customer_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(72.dp)
                        .align(Alignment.CenterHorizontally)
                        .clickable { photoMenuOpen = true },
                ) {
                    Avatar(name.ifBlank { "?" }, photoPath = photoPath, size = 72.dp)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp).align(Alignment.BottomEnd),
                    ) {
                        Icon(
                            Icons.Rounded.AddAPhoto,
                            contentDescription = stringResource(R.string.customer_change_photo),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                    DropdownMenu(expanded = photoMenuOpen, onDismissRequest = { photoMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.customer_choose_gallery)) },
                            onClick = {
                                photoMenuOpen = false
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.customer_take_photo)) },
                            onClick = { photoMenuOpen = false; launchCamera() },
                        )
                    }
                }
                VoiceOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.customer_name)) },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    typing = TypingContext.CUSTOMER,
                    modifier = Modifier.focusRequester(focus),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.customer_phone)) },
                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        }) {
                            Icon(Icons.Rounded.Contacts, contentDescription = stringResource(R.string.customer_pick_contact))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                VoiceOutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.customer_address)) },
                    leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), phone.trim().ifBlank { null }, address.trim().ifBlank { null }, photoPath) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
