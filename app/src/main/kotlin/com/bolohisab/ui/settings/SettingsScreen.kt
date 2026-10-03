package com.bolohisab.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.data.settings.AppLanguage
import com.bolohisab.data.settings.DigitStyle
import com.bolohisab.ui.components.PinKeypadScreen
import com.bolohisab.ui.components.formWidth
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    displayViewModel: DisplaySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val digitStyle by displayViewModel.digitStyle.collectAsStateWithLifecycle()
    val language by displayViewModel.language.collectAsStateWithLifecycle()
    val banglishTyping by displayViewModel.banglishTyping.collectAsStateWithLifecycle()

    if (state.pinSetup != PinSetupStep.Hidden) {
        PinSetupOverlay(state, viewModel)
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingExportPassphrase by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) backupViewModel.performExport(uri, pendingExportPassphrase) else backupViewModel.cancel()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupViewModel.startRestore(uri)
    }

    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)
    val wrongPassphrase = stringResource(R.string.backup_wrong_passphrase)
    val badFile = stringResource(R.string.backup_bad_file)
    LaunchedEffect(backup.message) {
        val text = when (backup.message) {
            BackupMessage.ExportSuccess -> exportSuccess
            BackupMessage.ImportSuccess -> importSuccess
            BackupMessage.WrongPassphrase -> wrongPassphrase
            BackupMessage.BadFile -> badFile
            null -> null
        }
        if (text != null) {
            scope.launch { snackbar.showSnackbar(text) }
            backupViewModel.consumeMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.formWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.settings_digit_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                DigitStyleSelector(digitStyle, displayViewModel::setDigitStyle)

                Text(
                    stringResource(R.string.settings_language_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                )
                LanguageSelector(language, displayViewModel::setLanguage)
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_banglish_toggle)) },
                    supportingContent = { Text(stringResource(R.string.settings_banglish_hint)) },
                    trailingContent = {
                        Switch(checked = banglishTyping, onCheckedChange = displayViewModel::setBanglishTyping)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
                LearnedWordsItem()
                VoiceModelSetting()

                Text(
                    stringResource(R.string.settings_lock_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                )
                ListItem(
                    leadingContent = {
                        Icon(if (state.lockEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_lock_toggle)) },
                    supportingContent = { Text(stringResource(R.string.settings_lock_hint)) },
                    trailingContent = {
                        Switch(checked = state.lockEnabled, onCheckedChange = viewModel::onToggleLock)
                    },
                )
                if (state.lockEnabled) {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Password, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.settings_lock_change_pin)) },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::onChangePin),
                    )
                }

                Text(
                    stringResource(R.string.settings_backup_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_export_hint)) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = backupViewModel::startExport),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_backup_import)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_import_hint)) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        importLauncher.launch(arrayOf("application/octet-stream"))
                    },
                )
            }
        }
    }

    when (backup.step) {
        BackupStep.EnterNewPassphrase -> PassphraseDialog(
            title = stringResource(R.string.backup_passphrase_new_title),
            mismatch = backup.mismatch,
            onSubmit = { pass -> backupViewModel.onExportPassphraseEntered(pass) {} },
            onDismiss = backupViewModel::cancel,
        )
        is BackupStep.ConfirmNewPassphrase -> PassphraseDialog(
            title = stringResource(R.string.backup_passphrase_confirm_title),
            mismatch = backup.mismatch,
            onSubmit = { pass ->
                backupViewModel.onExportPassphraseEntered(pass) { ready ->
                    pendingExportPassphrase = ready
                    exportLauncher.launch("bolo-hisab-backup-${LocalDate.now()}.bhbackup")
                }
            },
            onDismiss = backupViewModel::cancel,
        )
        BackupStep.ConfirmRestore -> RestoreWarningDialog(
            onConfirm = backupViewModel::confirmRestoreWarning,
            onDismiss = backupViewModel::cancel,
        )
        BackupStep.EnterRestorePassphrase -> PassphraseDialog(
            title = stringResource(R.string.backup_passphrase_restore_title),
            mismatch = false,
            onSubmit = backupViewModel::onRestorePassphraseEntered,
            onDismiss = backupViewModel::cancel,
        )
        BackupStep.Idle -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DigitStyleSelector(selected: DigitStyle, onSelect: (DigitStyle) -> Unit) {
    val styles = listOf(DigitStyle.BENGALI, DigitStyle.LATIN)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        styles.forEachIndexed { i, style ->
            SegmentedButton(
                selected = selected == style,
                onClick = { onSelect(style) },
                shape = SegmentedButtonDefaults.itemShape(i, styles.size),
            ) {
                Text(
                    stringResource(if (style == DigitStyle.BENGALI) R.string.settings_digit_bengali else R.string.settings_digit_latin),
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val languages = listOf(AppLanguage.BANGLA, AppLanguage.ENGLISH)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        languages.forEachIndexed { i, lang ->
            SegmentedButton(
                selected = selected == lang,
                onClick = { onSelect(lang) },
                shape = SegmentedButtonDefaults.itemShape(i, languages.size),
            ) {
                Text(
                    stringResource(if (lang == AppLanguage.BANGLA) R.string.settings_language_bangla else R.string.settings_language_english),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PinSetupOverlay(state: SettingsUiState, viewModel: SettingsViewModel) {
    val title = when (val step = state.pinSetup) {
        PinSetupStep.EnterNew -> stringResource(R.string.lock_set_title)
        is PinSetupStep.ConfirmNew -> stringResource(R.string.lock_confirm_title)
        PinSetupStep.Hidden -> ""
    }
    Box(Modifier.fillMaxSize()) {
        PinKeypadScreen(
            title = title,
            error = state.mismatch,
            errorMessage = stringResource(R.string.lock_mismatch),
            onSubmit = viewModel::onPinEntered,
            onErrorShown = viewModel::consumeMismatch,
        )
        TextButton(onClick = viewModel::onCancelPinSetup, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
