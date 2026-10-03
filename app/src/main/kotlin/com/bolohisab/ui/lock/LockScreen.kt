package com.bolohisab.ui.lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        )
    }
}
