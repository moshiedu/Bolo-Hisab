package com.bolohisab.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolohisab.data.backup.BackupCrypto
import com.bolohisab.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface BackupStep {
    data object Idle : BackupStep
    data object EnterNewPassphrase : BackupStep
    data class ConfirmNewPassphrase(val first: String) : BackupStep
    data object ConfirmRestore : BackupStep
    data object EnterRestorePassphrase : BackupStep
}

sealed interface BackupMessage {
    data object ExportSuccess : BackupMessage
    data object ImportSuccess : BackupMessage
    data object BadFile : BackupMessage
    data object WrongPassphrase : BackupMessage
}

data class BackupUiState(
    val step: BackupStep = BackupStep.Idle,
    val mismatch: Boolean = false,
    val working: Boolean = false,
    val message: BackupMessage? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var pendingExportPassphrase: String? = null
    private var pendingRestoreBytes: ByteArray? = null

    fun startExport() = _state.update { it.copy(step = BackupStep.EnterNewPassphrase, mismatch = false) }

    fun startRestore(uri: Uri) {
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                _state.update { it.copy(working = false, message = BackupMessage.BadFile) }
            } else {
                pendingRestoreBytes = bytes
                _state.update { it.copy(working = false, step = BackupStep.ConfirmRestore) }
            }
        }
    }

    fun confirmRestoreWarning() = _state.update { it.copy(step = BackupStep.EnterRestorePassphrase) }

    fun cancel() {
        pendingExportPassphrase = null
        pendingRestoreBytes = null
        _state.update { it.copy(step = BackupStep.Idle, mismatch = false) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Returns the passphrase to encrypt with once both entries match, so the caller can open the file picker. */
    fun onExportPassphraseEntered(pass: String, onReady: (String) -> Unit) {
        when (val step = _state.value.step) {
            BackupStep.EnterNewPassphrase ->
                _state.update { it.copy(step = BackupStep.ConfirmNewPassphrase(pass), mismatch = false) }
            is BackupStep.ConfirmNewPassphrase ->
                if (pass == step.first) {
                    pendingExportPassphrase = pass
                    _state.update { it.copy(step = BackupStep.Idle) }
                    onReady(pass)
                } else {
                    _state.update { it.copy(step = BackupStep.EnterNewPassphrase, mismatch = true) }
                }
            else -> Unit
        }
    }

    fun performExport(uri: Uri, passphrase: String) {
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { backupManager.export(passphrase) }
            val written = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }.isSuccess
            }
            pendingExportPassphrase = null
            _state.update {
                it.copy(working = false, message = if (written) BackupMessage.ExportSuccess else BackupMessage.BadFile)
            }
        }
    }

    fun onRestorePassphraseEntered(passphrase: String) {
        val bytes = pendingRestoreBytes ?: return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { backupManager.import(bytes, passphrase) }
                pendingRestoreBytes = null
                _state.update { it.copy(working = false, step = BackupStep.Idle, message = BackupMessage.ImportSuccess) }
            } catch (e: BackupCrypto.WrongPassphraseException) {
                _state.update { it.copy(working = false, message = BackupMessage.WrongPassphrase) }
            }
        }
    }
}
