package com.bolohisab.ui.lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.ui.components.PinKeypadScreen
import com.bolohisab.ui.format.Bn
import kotlinx.coroutines.delay

/** Shown in place of the whole app while app lock is on and the PIN has not been entered yet. */
@Composable
fun LockScreen(viewModel: LockGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val until = state.lockedUntil
    var remainingSec by remember { mutableLongStateOf(0L) }

    LaunchedEffect(until) {
        if (until == null) { remainingSec = 0; return@LaunchedEffect }
        while (true) {
            remainingSec = ((until - System.currentTimeMillis()) + 999) / 1000
            if (remainingSec <= 0) { viewModel.onLockoutEnded(); break }
            delay(250)
        }
    }

    // Fingerprint first when it's on: the prompt opens by itself each time the app locks, and the
    // keypad's fingerprint key brings it back after "use PIN" or a cancel.
    val context = LocalContext.current
    val title = stringResource(R.string.lock_biometric_title)
    val usePin = stringResource(R.string.lock_biometric_use_pin)
    val fingerprint = state.biometricEnabled && Biometrics.available(context)
    fun askFingerprint() = Biometrics.prompt(context, title, usePin, viewModel::onBiometricSuccess)
    LaunchedEffect(fingerprint) { if (fingerprint) askFingerprint() }

    val lockedOut = until != null && remainingSec > 0
    Surface(Modifier.fillMaxSize()) {
        PinKeypadScreen(
            title = stringResource(R.string.lock_enter_title),
            error = state.wrongPin || lockedOut,
            errorMessage = if (lockedOut) {
                val clock = "%d:%02d".format(remainingSec / 60, remainingSec % 60)
                stringResource(R.string.lock_wait, Bn.digits(clock))
            } else {
                stringResource(R.string.lock_wrong)
            },
            onSubmit = viewModel::tryUnlock,
            onErrorShown = viewModel::consumeWrongPin,
            enabled = !lockedOut && !state.checking,
            extraKey = if (fingerprint) {
                {
                    IconButton(onClick = ::askFingerprint, modifier = Modifier.size(64.dp)) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            contentDescription = stringResource(R.string.lock_use_fingerprint),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}
